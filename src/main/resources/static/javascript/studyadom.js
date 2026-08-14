window.addEventListener("scroll", reveal);
function reveal() {
	var reveals = document.querySelectorAll(".reveal");
	for (var i = 0; i < reveals.length; i++) {
		var windowheight = window.innerHeight;
		var revealtop = reveals[i].getBoundingClientRect().top;
		var revealpoint = 150;

		if (revealtop < windowheight - revealpoint) {
			reveals[i].classList.add("active");
		} else {
			reveals[i].classList.remove("active");
		}
	}
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
function exist() {
	window.location.href = "applicationProcess.html";
}
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
var messageBox = document.querySelector(".js-message");
var btn = document.querySelector(".js-message-btn");
var card = document.querySelector(".js-profile-card");
var closeBtn = document.querySelectorAll(".js-message-close");

btn.addEventListener("click", function (e) {
	e.preventDefault();
	card.classList.add("active");
});

closeBtn.forEach(function (element, index) {
	console.log(element);
	element.addEventListener("click", function (e) {
		e.preventDefault();
		card.classList.remove("active");
	});
});
function courseApply() {
	location.href = "studyadom.html";
}
