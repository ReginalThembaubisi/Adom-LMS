var slides = document.querySelectorAll(".img-advert");
var btns = document.querySelectorAll(".btn1");
let currentSlide = 1;

// manual click slide
var manualNav = function (manual) {
	slides.forEach((slide) => {
		slide.classList.remove("active");
	});
	btns.forEach((btn) => {
		btn.classList.remove("active");
	});
	slides[manual].classList.add("active");
	btns[manual].classList.add("active");
};

btns.forEach((btn, i) => {
	btn.addEventListener("click", () => {
		manualNav(i);
		currentSlide = i;
	});
});

//Automatic slide
var repeat = function (activeClass) {
	let active = document.getElementsByClassName("active");
	let i = 1;

	var repeater = () => {
		setTimeout(function () {
			[...active].forEach((activeSlide) => {
				activeSlide.classList.remove("active");
			});
			slides[i].classList.add("active");
			btns[i].classList.add("active");
			i++;

			if (slides.length == i) {
				i = 0;
			}
			if (i >= slides.length) {
				return;
			}
			repeater();
		}, 7000);
	};
	repeater();
};
function regFinance() {
	swal("Hello world!");
}
function LearnBourd() {
	window.location.href = "write-dashboaurd.html";
}
