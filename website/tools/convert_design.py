"""
Converts the Claude Design canvas pages (*.dc.html) into React components for the website.

The design is the source of truth for layout and copy: re-export the canvas into
design/ and run this again to pick up design changes, rather than editing the generated
files in src/pages and src/components by hand.

    python3 tools/convert_design.py design/ src/

What it does, per .dc.html file:
  - markup inside <x-dc> (minus <helmet>) becomes JSX; inline style strings stay verbatim and
    are turned into style objects at runtime by s() from src/lib/style.js, imported as __style
    so a design loop variable called "s" can't hide it
  - {{ holes }} become expressions on the component's renderVals() result
  - <sc-if> / <sc-for> become conditionals / .map(); <dc-import> becomes a component
  - style-hover="..." becomes a generated CSS class (src/generated/hover.css)
  - design links (About.dc.html) become site routes (/about/)
  - the <script type="text/x-dc"> logic class becomes the React class, with render() added
"""
import hashlib
import json
import os
import re
import sys
from html.parser import HTMLParser

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}

# Design file -> (component name, route). Pages without a route are not part of the site.
PAGES = {
    "Main": ("HomePage", "/"),
    "About": ("AboutPage", "/about/"),
    "Services": ("ServicesPage", "/services/"),
    "Careers": ("CareersPage", "/careers/"),
    "Learnerships": ("LearnershipsPage", "/learnerships/"),
    "Internships": ("InternshipsPage", "/internships/"),
    "Placements": ("PlacementsPage", "/university-placements/"),
    "Courses": ("CoursesPage", "/courses/"),
    "Course": ("CoursePage", None),  # one route per course, see routes.js
    "Prospective": ("ProspectivePage", "/prospective-students/"),
    "Apply": ("ApplyPage", "/apply/"),
    "Status": ("StatusPage", "/status/"),
    "eLearning": ("ELearningPage", "/e-learning/"),
}
COMPONENTS = {"SiteHeader": "SiteHeader", "SiteFooter": "SiteFooter"}
# Pages the LMS replaces: links to them go to the LMS instead.
LMS_LINKS = {"Dashboard": "LMS_LOGIN", "Lecturer": "LMS_STAFF_LOGIN"}

# Uploaded design images -> files in public/assets.
BLOBS = {
    "e1caefe9bddc2b35eff0ea07ccfd4b5e": "about.jpg", "1aa645170006fce6f06f0005945fc632": "adom-logo.png",
    "7f86da5dee3142ec5f96aa53d3f465e0": "c1.jpg", "ede1655841d6b9e76e5a7e8d46879e08": "c2.jpeg",
    "713c72cbd2840a9b26deafcee1650ee4": "c3.jpg", "d971a119e0818d36572c15cd3582ae65": "c4.png",
    "3c6842aec6bfe0f6cc8435f3273f820e": "c5.jpg", "523ec1a9756a934d6427be14540af24a": "c6.jpg",
    "86cf5a85722981c2ed5daf0c5dc53c77": "c7.jpg", "6f3e0691e1e47751fbcdc9191af5e66a": "chris.jpeg",
    "94cd81f17e3d14fa7f8dfd1b650c0c48": "hero-design.webp", "4b1c09599d1110ed4d21419941dc681f": "hero-services.jpg",
    "de93424948aafa6006e3a6fec5d632e5": "hero-study.jpg", "9d08e0f556eaa44a7f6067d802a177c8": "services.jpg",
}

# Attribute names HTMLParser lower-cases that React needs in camelCase.
ATTR_NAMES = {
    "onclick": "onClick", "onmouseenter": "onMouseEnter", "onmouseleave": "onMouseLeave", "onsubmit": "onSubmit",
    "onchange": "onChange", "oninput": "onInput", "viewbox": "viewBox", "class": "className", "for": "htmlFor",
    "tabindex": "tabIndex", "maxlength": "maxLength", "autocomplete": "autoComplete", "readonly": "readOnly",
    "inputmode": "inputMode", "colspan": "colSpan", "rowspan": "rowSpan", "srcset": "srcSet",
    "crossorigin": "crossOrigin", "allowfullscreen": "allowFullScreen", "referrerpolicy": "referrerPolicy",
}
BOOLEAN_ATTRS = {"required", "disabled", "checked", "readOnly", "multiple", "selected", "autoFocus", "allowFullScreen"}
DROP_ATTRS = {"hint-size", "hint-placeholder-val", "hint-placeholder-count"}

HOLE = re.compile(r"\{\{\s*(.*?)\s*\}\}")


class Node:
    def __init__(self, tag, attrs=None, parent=None):
        self.tag, self.attrs, self.parent, self.children = tag, attrs or [], parent, []


class TreeBuilder(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = Node("#root")
        self.cur = self.root

    def handle_starttag(self, tag, attrs):
        node = Node(tag, attrs, self.cur)
        self.cur.children.append(node)
        if tag not in VOID:
            self.cur = node

    def handle_startendtag(self, tag, attrs):
        self.cur.children.append(Node(tag, attrs, self.cur))

    def handle_endtag(self, tag):
        n = self.cur
        while n is not self.root and n.tag != tag:
            n = n.parent
        if n is not self.root:
            self.cur = n.parent

    def handle_data(self, data):
        self.cur.children.append(data)


def camel(name):
    return re.sub(r"-([a-z])", lambda m: m.group(1).upper(), name)


class Converter:
    def __init__(self, hover_classes):
        self.hover = hover_classes

    # --- holes and values ---
    def expr(self, path, scope):
        path = path.strip()
        if re.fullmatch(r"-?\d+(\.\d+)?|true|false|null", path):
            return path
        head = re.split(r"[.\[]", path, 1)[0]
        return path if head in scope else "v." + path

    def link(self, value):
        """A design link -> (is_expression, value)."""
        m = re.fullmatch(r"([A-Za-z]+)\.dc\.html([?#].*)?", value)
        if not m:
            return False, value
        name, rest = m.group(1), m.group(2) or ""
        if name in LMS_LINKS:
            return True, LMS_LINKS[name]
        if name == "Course":
            return False, "/courses/" + rest
        route = PAGES.get(name, (None, None))[1]
        if route is None:
            raise ValueError("Link to a page that isn't on the site: " + value)
        return False, route + rest

    def value(self, raw, scope, style=False):
        """An attribute value -> JSX attribute value ("..." or {...})."""
        whole = HOLE.fullmatch(raw.strip()) if raw else None
        if whole:
            return "{" + self.expr(whole.group(1), scope) + "}"
        for blob, name in BLOBS.items():
            raw = raw.replace("/_blob/" + blob, "/assets/" + name)
        if HOLE.search(raw):
            parts = []
            last = 0
            for m in HOLE.finditer(raw):
                parts.append(raw[last:m.start()].replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${"))
                parts.append("${" + self.expr(m.group(1), scope) + "}")
                last = m.end()
            parts.append(raw[last:].replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${"))
            tpl = "`" + "".join(parts) + "`"
            return "{__style(" + tpl + ")}" if style else "{" + tpl + "}"
        if style:
            return "{__style(" + json.dumps(raw) + ")}"
        return "{" + json.dumps(raw) + "}"

    # --- nodes ---
    def attrs(self, node, scope):
        out = []
        classes = []
        for name, raw in node.attrs:
            raw = "" if raw is None else raw
            if name in DROP_ATTRS or name.startswith("data-dc"):
                continue
            if name == "style-hover":
                key = raw.strip()
                if key not in self.hover:
                    self.hover[key] = "h-" + hashlib.sha1(key.encode()).hexdigest()[:8]
                classes.append(self.hover[key])
                continue
            if name.startswith("sc-camel-"):
                name = camel(name[len("sc-camel-"):])
            name = ATTR_NAMES.get(name, name)
            if "-" in name and not (name.startswith("data-") or name.startswith("aria-")):
                name = camel(name)
            if name == "style":
                out.append("style=" + self.value(raw, scope, style=True))
                continue
            if name == "href":
                is_expr, val = self.link(raw)
                out.append("href={" + val + "}" if is_expr else "href=" + self.value(val, scope))
                continue
            if name in BOOLEAN_ATTRS and raw in ("", name):
                out.append(name)
                continue
            out.append(name + "=" + self.value(raw, scope))
        if classes:
            out.append('className="' + " ".join(classes) + '"')
        return (" " + " ".join(out)) if out else ""

    def text(self, t, scope):
        if not t.strip():
            return "" if "\n" in t else ' {" "} '
        t = re.sub(r"\s+", " ", t)
        pieces, last = [], 0
        for m in HOLE.finditer(t):
            if m.start() > last:
                pieces.append("{" + json.dumps(t[last:m.start()]) + "}")
            pieces.append("{" + self.expr(m.group(1), scope) + "}")
            last = m.end()
        if last < len(t):
            pieces.append("{" + json.dumps(t[last:]) + "}")
        return "".join(pieces)

    def children(self, node, scope):
        return "".join(self.node(c, scope) for c in node.children)

    def node(self, n, scope):
        if isinstance(n, str):
            return self.text(n, scope)
        tag = n.tag
        a = dict(n.attrs)
        if tag in ("helmet", "script", "style", "template", "title", "meta", "link"):
            return ""
        if tag == "sc-if":
            cond = HOLE.fullmatch(a["value"].strip()).group(1)
            return "{" + self.expr(cond, scope) + " ? (<>" + self.children(n, scope) + "</>) : null}"
        if tag == "sc-for":
            lst = HOLE.fullmatch(a["list"].strip()).group(1)
            var = a.get("as", "item")
            inner = self.children(n, scope | {var, "$index"})
            return ("{(" + self.expr(lst, scope) + " || []).map((" + var + ", $index) => (<React.Fragment key={$index}>"
                    + inner + "</React.Fragment>))}")
        if tag == "dc-import":
            comp = COMPONENTS[a["name"]]
            props = [(k, v) for k, v in n.attrs if k not in ("name",) and k not in DROP_ATTRS]
            fake = Node("x", props)
            return "<" + comp + self.attrs(fake, scope) + " />"
        if tag == "sc-raw-select":
            tag = "select"
        attrs = self.attrs(n, scope)
        if tag in VOID:
            return "<" + tag + attrs + " />"
        return "<" + tag + attrs + ">" + self.children(n, scope) + "</" + tag + ">"


def extract(html):
    body = re.search(r"<x-dc>(.*)</x-dc>", html, re.S).group(1)
    script = re.search(r'<script type="text/x-dc"[^>]*>(.*?)</script>', html, re.S)
    props = re.search(r'<script type="text/x-dc"[^>]*data-props=(["\'])(.*?)\1', html, re.S)
    title = re.search(r"<title>(.*?)</title>", html)
    return body, (script.group(1) if script else ""), (props.group(2) if props else None), (title.group(1) if title else None)


def convert_logic(js, name, jsx):
    js = js.strip()
    if not js:
        js = "class Component extends DCLogic {\n  renderVals() { return {}; }\n}"
    js = js.replace("class Component extends DCLogic", "class " + name + " extends React.Component", 1)
    js = js.replace("window.ADOM", "ADOM").replace("window.AdomAPI", "AdomAPI")
    for blob, file in BLOBS.items():
        js = js.replace("/_blob/" + blob, "/assets/" + file)
    js = js.replace("'Course.dc.html?c=' + ", "'/courses/' + ")
    js = re.sub(r"'([A-Za-z]+)\.dc\.html([^']*)'", lambda m: route_literal(m.group(1), m.group(2)), js)
    render = "\n  render() {\n    const v = this.renderVals();\n    return (" + jsx + ");\n  }\n"
    idx = js.rstrip().rfind("}")
    return js[:idx] + render + "}\n"


def route_literal(name, rest):
    if name in LMS_LINKS:
        return LMS_LINKS[name]
    route = PAGES.get(name, (None, None))[1]
    if route is None:
        raise ValueError("Link to a page that isn't on the site: " + name)
    return "'" + route + rest + "'"


def defaults(props_json):
    if not props_json:
        return {}
    try:
        data = json.loads(props_json.replace("&quot;", '"').replace("&amp;", "&").replace("&#39;", "'"))
    except json.JSONDecodeError:
        return {}
    return {k: v["default"] for k, v in data.items() if isinstance(v, dict) and "default" in v and not k.startswith("$")}


def main(src, out):
    hover = {}
    conv = Converter(hover)
    os.makedirs(os.path.join(out, "pages"), exist_ok=True)
    os.makedirs(os.path.join(out, "components"), exist_ok=True)
    os.makedirs(os.path.join(out, "generated"), exist_ok=True)
    titles = {}
    for file_stem, comp in list(PAGES.items()) + [(k, (v, None)) for k, v in COMPONENTS.items()]:
        name = comp[0]
        html = open(os.path.join(src, file_stem + ".dc.html"), encoding="utf-8").read()
        body, js, props, title = extract(html)
        tb = TreeBuilder()
        tb.feed(body)
        top = [c for c in tb.root.children if not isinstance(c, str) and c.tag != "helmet"]
        jsx = conv.node(top[0], set()) if len(top) == 1 else "<>" + "".join(conv.node(c, set()) for c in top) + "</>"
        logic = convert_logic(js, name, jsx)
        folder = "components" if file_stem in COMPONENTS else "pages"
        imports = ["import React from 'react';", "import { s as __style } from '../lib/style.js';",
                   "import { ADOM } from '../lib/data.js';", "import { AdomAPI, LMS_LOGIN, LMS_STAFF_LOGIN } from '../lib/api.js';"]
        used = [c for c in COMPONENTS.values() if "<" + c + " " in jsx]
        imports += ["import " + c + " from '../components/" + c + ".jsx';" for c in used]
        d = defaults(props)
        tail = ""
        if d:
            tail = name + ".defaultProps = " + json.dumps(d) + ";\n"
        header = "// Generated from design/" + file_stem + ".dc.html by tools/convert_design.py. Do not edit by hand.\n/* eslint-disable */\n"
        with open(os.path.join(out, folder, name + ".jsx"), "w", encoding="utf-8") as f:
            f.write(header + "\n".join(imports) + "\n\n" + logic + "\n" + tail + "export default " + name + ";\n")
        if title:
            titles[name] = title
    css = "/* Generated by tools/convert_design.py from style-hover attributes. */\n"
    for rule, cls in sorted(hover.items(), key=lambda x: x[1]):
        decls = ";".join(d.strip() + " !important" for d in rule.split(";") if d.strip())
        css += "." + cls + ":hover{" + decls + "}\n"
    open(os.path.join(out, "generated", "hover.css"), "w").write(css)
    json.dump(titles, open(os.path.join(out, "generated", "titles.json"), "w"), indent=2)
    print("Converted", len(PAGES) + len(COMPONENTS), "files;", len(hover), "hover styles.")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
