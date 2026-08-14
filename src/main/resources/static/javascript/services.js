if (window.innerWidth > 92) {
	document.querySelectorAll(" .nav-item").forEach(function (everyitem) {
		everyitem.addEventListener("mouseover", function (e) {
			let el_link = this.querySelector("a[data-toggle]");

			if (el_link != null) {
				let nextEl = el_link.nextElementSibling;
				el_link.classList.add("show");
				nextEl.classList.add("show");
			}
		});
		everyitem.addEventListener("mouseleave", function (e) {
			let el_link = this.querySelector("a[data-toggle]");

			if (el_link != null) {
				let nextEl = el_link.nextElementSibling;
				el_link.classList.remove("show");
				nextEl.classList.remove("show");
			}
		});
	});
}
function toggleMenu() {
	document.getElementById("chBTN").classList.toggle("active");
	
	document.querySelector(".nonebn").classList.toggle("now");
	
	document.querySelector(".nonebn1").classList.toggle("now");
	document.querySelector(".nonebn2").classList.toggle("now");
	document.querySelector(".nonebn999").classList.toggle("now");
	document.querySelector(".nonebn4").classList.toggle("now");
	document.querySelector(".nonebn5").classList.toggle("now");
	document.querySelector(".nonebn6").classList.toggle("now");
	document.querySelector(".nonebn7").classList.toggle("now");
	document.querySelector(".nonebn8").classList.toggle("now");
	document.querySelector(".nonebn9").classList.toggle("now");
	document.querySelector(".nonebn10").classList.toggle("now");
	document.querySelector(".nonebn11").classList.toggle("now");
	document.querySelector(".nonebn12").classList.toggle("now");
	document.querySelector(".nonebn13").classList.toggle("now");
	document.querySelector(".nonebn14").classList.toggle("now");
	document.querySelector(".no123").classList.toggle("now2");

}