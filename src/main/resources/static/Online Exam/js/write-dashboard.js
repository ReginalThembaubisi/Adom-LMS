var slides = document.querySelectorAll(".divContainer");
var allBtns = document.querySelectorAll(".btnAll");
var btn1 = document.querySelector(".btn1");
var btn2 = document.querySelector(".btn2");
var btn3 = document.querySelector(".btn3");
var btn4 = document.querySelector(".btn4");
var btn5 = document.querySelector(".btn5");
var btn6 = document.querySelector(".btn6");
var btn7 = document.querySelector(".btn7");
let currentSlide = 0;

// manual click slide
var manualNav = function (manual, btn) {
	slides.forEach((slide) => {
		slide.classList.remove("active");
	});
	allBtns.forEach((btn) => {
		btn.classList.remove("active");
	});
	slides[manual].classList.add("active");
	allBtns[btn].classList.add("active");
};
var manualNavon = function (manual, btn) {
	slides.forEach((slide) => {
		slide.classList.remove("active");
	});
	allBtns.forEach((btn) => {
		btn.classList.remove("active");
	});
	slides[0].classList.add("active");
	allBtns[0].classList.add("active");
};

btn1.addEventListener("click", () => {
	var i = 0;
	var btn = 0;
	currentSlide = i;
	manualNav(i, btn);
});
btn2.addEventListener("click", () => {
	var i = 1;
	var btn = 1;
	currentSlide = i;
	manualNav(i, btn);
});
btn3.addEventListener("click", () => {
	var i = 2;
	var btn = 2;
	currentSlide = i;
	manualNav(i, btn);
});
btn4.addEventListener("click", () => {
	var i = 3;
	var btn = 3;
	currentSlide = i;
	manualNav(i, btn);
});
btn5.addEventListener("click", () => {
	var i = 4;
	var btn = 4;
	currentSlide = i;
	manualNav(i, btn);
});
btn6.addEventListener("click", () => {
	var i = 5;
	var btn = 5;
	currentSlide = i;
	manualNav(i, btn);
});
btn7.addEventListener("click", () => {
	var i = 6;
	var btn = 6;
	currentSlide = i;
	manualNav(i, btn);
});
