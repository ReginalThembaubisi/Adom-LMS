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

function staffDisplay() {
	auth.onAuthStateChanged((user) => {
		if (user) {
			db.collection("Staff")
				.doc(auth.currentUser.uid)
				.get()
				.then((snaps) => {
					document.getElementById("NameInit2").innerHTML =
						snaps.data().Title +
						" " +
						snaps.data().Name +
						" " +
						snaps.data().Surname;
					document.getElementById("NameInit").innerHTML =
						snaps.data().Title +
						" " +
						snaps.data().Name +
						" " +
						snaps.data().Surname;

					document.getElementById("email").innerHTML =
						snaps.data().Email;
					document.getElementById("idNumber").innerHTML =
						snaps.data().ID_NO;

					document.getElementById("enrolled-course").innerHTML =
						snaps.data().Course;
					document.getElementById(
						"ann-title"
					).innerHTML = `<option value="${snaps.data().Course}">${
						snaps.data().Course
					}</option>`;
					document.getElementById(
						"ann-title2"
					).innerHTML = `<option value="${snaps.data().Course}">${
						snaps.data().Course
					}</option>`;

					document.getElementById(
						"ann-title3"
					).innerHTML = `<option value="${snaps.data().Course}">${
						snaps.data().Course
					}</option>`;

					document.getElementById(
						"ann-title5"
					).innerHTML = `<option value="${snaps.data().Course}">${
						snaps.data().Course
					}</option>`;

					displayNotes(snaps.data().Course);
					displayActivity(snaps.data().Course);
					displayMeeting(snaps.data().Course);
				});
		}
	});
}
staffDisplay();
function postAnn() {
	var annTitle = document.getElementById("ann-title").value;
	var annCont = document.getElementById("ann-content").value;
	var date = new Date();
	var year = date.getFullYear();
	var month = date.getMonth() + 1;
	var timestamp = date.getTime();
	var today = date.getDate();
	var time = date.getHours() + ":" + date.getMinutes();
	var DatePosted = today + " / " + month + " / " + year + " / " + time;

	console.log(today + " - " + month + " - " + year + " - " + time);

	db.collection("Announcements")
		.add({
			Date: DatePosted,
			Content: annCont,
			Course: annTitle,
			TimeStamps: timestamp,
		})
		.then(() => {
			swal("Successful", "Posted successfully", "success", {
				buttons: false,
				timer: 2000,
			});
			document.getElementById("ann-content").value = "";
		});
}
function postNotes() {
	var Course = document.getElementById("ann-title2").value;
	var chapter = document.getElementById("chapterCourse").value;
	var linkNotes = document.getElementById("linkNote").value;
	const ref = firebase.storage().ref("Notes/");
	const file = document.querySelector("#filenote").files[0];

	if (file == "") {
		swal("Please Select Results before you can Upload!");
		return;
	}
	var fname = "Notes";
	var today = new Date();
	var time = today.getTime();

	const name = +new Date() + "-" + file.name;
	const theName = file.name;
	const metadata = {
		contentType: file.type,
	};

	const task = ref.child(name).put(file, metadata);
	task.on("state_changed", function progress(snapshot) {
		var percentage =
			(snapshot.bytesTransferred / snapshot.totalBytes) * 100;
		document.getElementById("progress-status").value = percentage;
		document.getElementById("UpProgress2").innerHTML =
			"Upload " + percentage.toFixed(0) + "%";
	});

	task.then((snapshot) => snapshot.ref.getDownloadURL())
		.then((url) => {
			console.log(url);

			db.collection("EnrolledCourses")
				.doc(Course)
				.collection("Notes")
				.add({
					File_name: theName,
					Time: time,
					Url: url,
					Chapter: chapter,
					CourseName: Course,
					LinkNote: linkNotes,
				})
				.then(() => {
					swal("Successful", "Notes added", "success");
				});
		})
		.catch(console.error);
}

function postActivity() {
	var Course = document.getElementById("ann-title3").value;
	var activity = document.getElementById("activityCourse").value;

	const ref = firebase.storage().ref("Activities/");
	const file = document.querySelector("#file2").files[0];

	if (file == "") {
		swal("Please Select Results before you can Upload!");
		return;
	}
	var fname = "Notes";
	var today = new Date();
	var time = today.getTime();

	const name = +new Date() + "-" + file.name;
	const theName = file.name;
	const metadata = {
		contentType: file.type,
	};

	const task = ref.child(name).put(file, metadata);
	task.on("state_changed", function progress(snapshot) {
		var percentage =
			(snapshot.bytesTransferred / snapshot.totalBytes) * 100;
		document.getElementById("progress2").value = percentage;
		document.getElementById("per-up2").innerHTML =
			"Upload " + percentage.toFixed(0) + "%";
	});

	task.then((snapshot) => snapshot.ref.getDownloadURL())
		.then((url) => {
			console.log(url);

			db.collection("EnrolledCourses")
				.doc(Course)
				.collection("Activities")
				.add({
					File_name: theName,
					Time: time,
					Url: url,
					Chapter: activity,
					CourseName: Course,
				})
				.then(() => {
					swal("Successful", "Activity added", "success");
				});
		})
		.catch(console.error);
}

function postExam() {
	var Course = document.getElementById("ann-title3").value;
	var activity = document.getElementById("activityCourse").value;

	const ref = firebase.storage().ref("Activities/");
	const file = document.querySelector("#file2").files[0];

	if (file == "") {
		swal("Please Select Results before you can Upload!");
		return;
	}
	var fname = "Notes";
	var today = new Date();
	var time = today.getTime();

	const name = +new Date() + "-" + file.name;
	const theName = file.name;
	const metadata = {
		contentType: file.type,
	};

	const task = ref.child(name).put(file, metadata);
	task.on("state_changed", function progress(snapshot) {
		var percentage =
			(snapshot.bytesTransferred / snapshot.totalBytes) * 100;
		document.getElementById("progress2").value = percentage;
		document.getElementById("per-up2").innerHTML =
			"Upload " + percentage.toFixed(0) + "%";
	});

	task.then((snapshot) => snapshot.ref.getDownloadURL())
		.then((url) => {
			console.log(url);

			db.collection("EnrolledCourses")
				.doc(Course)
				.collection("Activities")
				.add({
					File_name: theName,
					Time: time,
					Url: url,
					Chapter: activity,
					CourseName: Course,
				})
				.then(() => {
					swal("Successful", "Activity added", "success");
				});
		})
		.catch(console.error);
}
function displayNotes(Course) {
	db.collection("EnrolledCourses")
		.doc(Course)
		.collection("Notes")
		.onSnapshot((snapshot) => {
			const list = document.getElementById("listofnotes");
			var div = "";
			var html = "";
			snapshot.forEach((element) => {
				div = `<div class="chapter">
            <li>
            
            <h3><i class="fas fa-solid fa-folder-open"></i> ${
				element.data().Chapter
			}</h3>
            <div class="listofchapers">
            <a href="${element.data().Url}" download  target="_blank">
            <i class="fas fa-file-download"></i> ${element.data().File_name}
            </a>
            
            
            
            </div>`;
				html += div;
				list.innerHTML = html;
			});
		});
}
function displayActivity(Course) {
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
            
            <h3><i class="fas fa-solid fa-folder-open"></i> ${
				element.data().Chapter
			}</h3>
            <div class="listofchapers">
            <a href="${element.data().Url}" download  target="_blank">
            <i class="fas fa-file-download"></i> ${element.data().File_name}
            </a>
            
            
            
            </div>`;
				html += div;
				list.innerHTML = html;
			});
		});
}
function postMeeting() {
	var Course = document.getElementById("ann-title5").value;
	var Link = document.getElementById("theLink").value;
	var Hostlink = document.getElementById("theHost").value;
	db.collection("EnrolledCourses")
		.doc(Course)
		.collection("OnMeetings")
		.doc("96325")
		.set({
			MeetingLink: Link,
			HostLink: Hostlink,
		})
		.then(() => {
			swal("Successful", "Meeting Created succesfully", "success");
		});
}
function displayMeeting(Course) {
	db.collection("EnrolledCourses")
		.doc(Course)
		.collection("OnMeetings")
		.doc("96325")
		.onSnapshot((snapsdata) => {
			document.getElementById("joinbtn").innerHTML = `<a href="${
				snapsdata.data().MeetingLink
			}" class="btn  btn-primary" target="_blank">Join Online Class</a> &nbsp; &nbsp; &nbsp;
            <a href="${
				snapsdata.data().HostLink
			}" class="btn  btn-primary" target="_blank">Start Meeting</a> 
            `;
		});
}
const logout = document.querySelector(".logout_profiletop");
logout.addEventListener("click", (e) => {
	auth.signOut().then(() => {
		console.log("user signed out");
		location.href = "dist/index.html";
	});
});

function goTestPortal() {
	location.href = "Online Exam/admin.html";
}
