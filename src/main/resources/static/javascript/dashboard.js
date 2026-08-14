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
	window.location.href = "dash-register.html";
}
function LearnBourd() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	if(window.screen.availWidth <810){
		alert("Your Size of screen is not supported for online learning USE A Tablet, LAPTOP or DEKSTOP")
		return false
	} else{
window.location.href = "write-dashboaurd.html?User=" + ID + "";
	}
	
}
function registration() {
	window.location.href = "nowRegAdom.html";
}

function displayPersonal() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("personalDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			console.log(snaps.data().RSA_ID);

			document.getElementById("NameInit").innerHTML = `<span>${
				snaps.data().Name
			} ${snaps.data().Surname}</span>`;
		});
}
displayPersonal();
function logout() {
	auth.signOut().then(() => {
		console.log("user signed out");
		location.href = "studentLogin.html";
	});
}
