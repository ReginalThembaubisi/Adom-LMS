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

const messForm = document.getElementById("form20");
messForm.addEventListener("submit", (e) => {
	e.preventDefault();

	var myName = document.getElementById("name-form").value;
	if (myName == "") {
		swal("Please enter Your name !");
		return false;
	}
	var email = document.getElementById("email-form").value;
	if (email == "") {
		swal("Please enter email address!");
		return false;
	}
	var messageForm = document.getElementById("message-form").value;
	if (messageForm == "") {
		swal("Please enter Your Message !");
		return false;
	}
	Email.send({
		Host: "smtp.gmail.com",
		Username: "chris.adomtech@gmail.com",
		Password: "rznrvvqwumcyanvo",
		To: "chris.adomtech@gmail.com",
		From: email,
		Subject: `${myName} sent you a message`,
		Body: `<p style="font-weight:bold;">From: ${myName}</p><br /><p>${messageForm}</p>  <br /> <p>Kind Regards<br />${myName} `,
	}).then((message) => {
		swal("Successful", "Your message has been sent", "success");
		document.getElementById("message-form").value = "";
		document.getElementById("email-form").value = "";
		document.getElementById("name-form").value = "";
	});
});
function sendAppointment() {
	swal("Successful", "Your appointment has been made", "success");

	setTimeout(() => {
		window.location.reload();
	}, 5000);
}
function about() {
	location.href = "AboutUs.html";
}
function viewCourse() {
	location.href = "Courses.html";
}
