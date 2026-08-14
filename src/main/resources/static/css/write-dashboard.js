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
			document.getElementById("NameInit").innerHTML = `<span>${
				snaps.data().Initials
			} ${snaps.data().Surname}</span>`;
			document.getElementById("NameInit2").innerHTML = `<span>${
				snaps.data().Initials
			} ${snaps.data().Surname}</span>`;
		});

	db.collection("studentApplication")
		.doc(ID)
		.collection("contactDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("email").innerHTML = snaps.data().Email;
		});
}
displayPersonal();
function logout() {
	auth.signOut().then(() => {
		console.log("user signed out");
		location.href = "studentLogin.html";
	});
}
function Announcements() {
	db.collection("Announcements")
		.orderBy("TimeStamps", "desc")
		.onSnapshot((data) => {
			var div = "";
			var html = "";
			const list = document.getElementById("Listofann");
			data.forEach((element) => {
				div = `<div class="row update">
				<div class="col-6 left-notification">
					<span>${element.data().Date}</span>
				</div>
				<div class="col-6 right-notification">
					<div class="logo-noti">
						<i class="fas fa-bookmark"></i>
					</div>
					<span class="titleM"
						>${element.data().Course}</span
					>
					<br />
					<span class="action-done"
						>Announcement posted: </span
					>
					<br />
					<p class="content-post">
						${element.data().Content}
					</p>
				</div>
			</div>`;
				html += div;
				list.innerHTML = html;
			});
		});
}
Announcements();
function RegisteredCourse() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("registeredCourses")
		.doc("96325")
		.collection("Modules")

		.onSnapshot((data) => {
			var div = "";
			var html = "";
			const list = document.getElementById("ListofcOURSE");
			data.forEach((element) => {
				div = `	<div class="col-md-4 course" onclick="ViewCourse('${
					element.data().CourseName
				}')">
				<div class="out">
					<img src="img/learn-bourd/course1.jpg" alt="" />
					<div class="title-of-course">
						<span class="module-code">NQF Level: ${element.data().NQFLevel}</span
						><br />
						<span class="module-name"
							>${element.data().ShortModuleName}</span
						><br />
						<span class="lecture-name"
							>Lecture: ${element.data().Lecture}</span
						>
					</div>
				</div>
			</div>`;
				html += div;
				list.innerHTML = html;
			});
		});
}
RegisteredCourse();

function ViewCourse(Course) {
	console.log(Course);

	db.collection("EnrolledCourses")
		.doc(Course)
		.collection("Notes")
		.orderBy("Time", "asc")
		.onSnapshot((snapshot) => {
			const list = document.getElementById("listofnotes");
			var div = "";
			var html = "";
			snapshot.forEach((element) => {
				div = `<div class="chapter">
				<li>
				
				<h3><i class="fas fa-solid fa-folder-open"></i> ${element.data().Chapter}</h3>
				<div class="listofchapers">
				<a href="${element.data().Url}" download  target="_blank">
				<i class="fas fa-file-download"></i> ${element.data().File_name}
				</a>
				
				
				
				</div>`;
				html += div;
				list.innerHTML = html;
			});
		});

	db.collection("EnrolledCourses")
		.doc(Course)
		.collection("Activities")
		.onSnapshot((snapshot) => {
			const list = document.getElementById("listofactivities");
			var div = "";
			var html = "";
			snapshot.forEach((element) => {
				div = `<div class="chapter">
					<li>
					
					<h3><i class="fas fa-solid fa-folder-open"></i> ${element.data().Chapter}</h3>
					<div class="listofchapers">
					<a href="${element.data().Url}" download  target="_blank">
					<i class="fas fa-file-download"></i> ${element.data().File_name}
					</a>
					
					
					
					</div>`;
				html += div;
				list.innerHTML = html;
			});
		});

	db.collection("EnrolledCourses")
		.doc(Course)
		.collection("OnMeetings")
		.doc("96325")
		.onSnapshot((snapsdata) => {
			document.getElementById("joinbtn").innerHTML = `<a href="${
				snapsdata.data().MeetingLink
			}" class="btn  btn-primary" target="_blank">Join Online Class</a> 
            `;
		});

	db.collection("Staff")
		.where("Course", "==", Course)
		.get()
		.then((snaps) => {
			snaps.forEach((element) => {
				console.log(element.data().Title);
				document.getElementById("NameInit2Lecture").innerHTML =
					element.data().Title +
					" " +
					element.data().Name +
					" " +
					element.data().Surname;
				document.getElementById("NameInitLecture").innerHTML =
					element.data().Title +
					" " +
					element.data().Name +
					" " +
					element.data().Surname;

				document.getElementById("emailLecture").innerHTML =
					element.data().Email;
			});
		});

	document.getElementById("courseProfile").classList.remove("active");
}
function hideCourse() {
	console.log("running");
	document.getElementById("courseProfile").classList.add("active");
}

var divs = document.querySelectorAll(".title22");
var allBtns2 = document.querySelectorAll(".btnAll2");
var btn11 = document.querySelector(".btn11");
var btn21 = document.querySelector(".btn12");
var btn31 = document.querySelector(".btn13");
var btn41 = document.querySelector(".btn14");
var btn51 = document.querySelector(".btn15");
var btn61 = document.querySelector(".btn16");
var btn71 = document.querySelector(".btn17");
var btn81 = document.querySelector(".btn18");
var btn91 = document.querySelector(".btn19");
let currentSlide1 = 0;

// manual click slide
var manualNav1 = function (manual, btn) {
	divs.forEach((slide) => {
		slide.classList.remove("active");
	});
	allBtns2.forEach((btn) => {
		btn.classList.remove("active");
	});
	divs[manual].classList.add("active");
	allBtns2[btn].classList.add("active");
};
var manualNavon1 = function (manual, btn) {
	divs.forEach((slide) => {
		slide.classList.remove("active");
	});
	allBtns2.forEach((btn) => {
		btn.classList.remove("active");
	});
	divs[0].classList.add("active");
	allBtns2[0].classList.add("active");
};

btn11.addEventListener("click", () => {
	var i = 0;
	var btn = 0;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn21.addEventListener("click", () => {
	var i = 1;
	var btn = 1;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn31.addEventListener("click", () => {
	var i = 2;
	var btn = 2;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn41.addEventListener("click", () => {
	var i = 3;
	var btn = 3;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn51.addEventListener("click", () => {
	var i = 4;
	var btn = 4;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn61.addEventListener("click", () => {
	var i = 5;
	var btn = 5;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn71.addEventListener("click", () => {
	var i = 6;
	var btn = 6;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn81.addEventListener("click", () => {
	var i = 7;
	var btn = 7;
	currentSlide1 = i;
	manualNav1(i, btn);
});
btn91.addEventListener("click", () => {
	var i = 8;
	var btn = 8;
	currentSlide1 = i;
	manualNav1(i, btn);
});
