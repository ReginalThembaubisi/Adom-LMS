var slides = document.querySelectorAll(".divContainer");
var allBtns = document.querySelectorAll(".list-group-item");
var btn1 = document.querySelector(".btn1");
var btn2 = document.querySelector(".btn2");
var btn3 = document.querySelector(".btn3");
var btn4 = document.querySelector(".btn4");
var btn5 = document.querySelector(".btn5");
var btn6 = document.querySelector(".btn6");
var btn7 = document.querySelector(".btn7");
var btn8 = document.querySelector(".btn8");

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
btn7.addEventListener("click", () => {
	var i = 7;
	var btn = 7;
	currentSlide = i;
	manualNav(i, btn);
});

function addCourses() {
	var faculty = document.getElementById("faculty").value;
	var courseName = document.getElementById("courseName").value;
	var courseCode = document.getElementById("courseCode").value;

	var spaceAvailable = document.getElementById("spaceAvailable").value;

	db.collection("Admin")
		.doc("Admission")
		.collection("Courses")
		.add({
			Faculty: faculty,
			Course: courseName,
			Course_Code: courseCode,

			Space_available: spaceAvailable,
		})
		.then(() => {
			swal("Successful", "Course Added successfully", "success", {
				buttons: false,
				timer: 2000,
			});
			document.getElementById("faculty").value = "";
			document.getElementById("courseName").value = "";
			document.getElementById("levelCourse").value = "";
			document.getElementById("spaceAvailable").value = "";
			document.getElementById("courseCode").value = "";
		});
}
function listOfcourses() {
	var html = "";
	var div = "";
	db.collection("Admin")
		.doc("Admission")
		.collection("Courses")
		.onSnapshot((docs) => {
			const list = document.getElementById("list");
			var count = 0;
			docs.forEach((snap) => {
				count++;
				div = `
                <tr>
                                <th scope="row">${count}</th>
                                <td>${snap.data().Course_Code}</td>
                                <td>${snap.data().Course}</td>
                                <td>${snap.data().Space_available}</td>
                            </tr>
                           
                `;
				html += div;
				list.innerHTML = html;
				var total = count;
				document.getElementById("courseTotal").innerHTML = total;
			});
		});
}
function getlistAllApplicants() {
	var html = "";
	var div = "";

	db.collection("Admin")
		.doc("Admission")
		.collection("submittedApplications")
		.get()
		.then((snaps) => {
			var count = -1;
			let list = [];
			snaps.forEach((snap) => {
				count++;
				list[count] = snap.data().ID;
			});
			theList(list);
		});
}
getlistAllApplicants();
function getlistOfAccepted() {
	var html = "";
	var div = "";

	db.collection("Admin")
		.doc("Admission")
		.collection("acceptedApplications")
		.get()
		.then((snaps) => {
			var countAccepted = 0;
			var count = -1;
			let list = [];
			snaps.forEach((snap) => {
				countAccepted++;
				count++;
				list[count] = snap.data().ID;

				document.getElementById("acceptss").innerHTML = countAccepted;
			});
			theListAccepted(list);
		});
}
getlistOfAccepted();

function theListAccepted(L) {
	for (let i = 0; i < L.length; i++) {
		const list = document.querySelector("#appliedListAdmitted");
		var html = "";
		var div = "";
		var count = 0;
		db.collection("studentApplication")
			.doc(L[i])
			.collection("personalDetails")
			.doc("96325")
			.get()
			.then((snap) => {
				console.log();

				db.collection("studentApplication")
					.doc(L[i])
					.collection("statusOfapplication")
					.doc("96325")
					.get()
					.then((docs) => {
						console.log(docs.data().StudentNo);

						db.collection("studentApplication")
							.doc(L[i])
							.collection("appliedCourses")

							.get()
							.then((coz) => {
								coz.forEach((cz) => {
									count++;
									div = `
									<tr  data-bs-toggle="modal" data-bs-target="#exampleModal1" onclick="get('${
										L[i]
									}')">
                                    <th scope="row">${count}</th>
                                    <td>${snap.data().Initials} ${
										snap.data().Surname
									}</td>
                                    <td>${docs.data().StudentNo}</td>
                                    <td>${cz.data().Course}</td>
                                    <td>${docs.data().Status_Application}</td>
                                </tr>
									`;
								});
								html += div;
								list.innerHTML = html;
							});
					});
			});
	}
}

getlistAllApplicants();
function getlistOfRejected() {
	var html = "";
	var div = "";

	db.collection("Admin")
		.doc("Admission")
		.collection("rejecetdApplications")
		.get()
		.then((snaps) => {
			var countAccepted = 0;
			var count = -1;
			let list = [];
			snaps.forEach((snap) => {
				countAccepted++;
				count++;
				list[count] = snap.data().ID;

				document.getElementById("rejects").innerHTML = countAccepted;
			});
			theListRejected(list);
		});
}
getlistOfRejected();

function theListRejected(L) {
	for (let i = 0; i < L.length; i++) {
		const list = document.querySelector("#appliedListRejected");
		var html = "";
		var div = "";
		var count = 0;
		db.collection("studentApplication")
			.doc(L[i])
			.collection("personalDetails")
			.doc("96325")
			.get()
			.then((snap) => {
				console.log();

				db.collection("studentApplication")
					.doc(L[i])
					.collection("statusOfapplication")
					.doc("96325")
					.get()
					.then((docs) => {
						console.log(docs.data().StudentNo);

						db.collection("studentApplication")
							.doc(L[i])
							.collection("appliedCourses")

							.get()
							.then((coz) => {
								coz.forEach((cz) => {
									count++;
									div = `
									<tr  data-bs-toggle="modal" data-bs-target="#exampleModal1" onclick="get('${
										L[i]
									}')">
                                    <th scope="row">${count}</th>
                                    <td>${snap.data().Initials} ${
										snap.data().Surname
									}</td>
                                    <td>${docs.data().StudentNo}</td>
                                    <td>${cz.data().Course}</td>
                                    <td>${docs.data().Status_Application}</td>
                                </tr>
									`;
								});
								html += div;
								list.innerHTML = html;
							});
					});
			});
	}
}

function theList(L) {
	for (let i = 0; i < L.length; i++) {
		const list = document.querySelector("#appliedList");
		var html = "";
		var div = "";
		var count = 0;
		db.collection("studentApplication")
			.doc(L[i])
			.collection("personalDetails")
			.doc("96325")
			.get()
			.then((snap) => {
				console.log();

				db.collection("studentApplication")
					.doc(L[i])
					.collection("statusOfapplication")
					.doc("96325")
					.get()
					.then((docs) => {
						console.log(docs.data().StudentNo);

						db.collection("studentApplication")
							.doc(L[i])
							.collection("appliedCourses")

							.get()
							.then((coz) => {
								coz.forEach((cz) => {
									count++;
									div = `
									<tr  data-bs-toggle="modal" data-bs-target="#exampleModal1" onclick="get('${
										L[i]
									}')">
                                    <th scope="row">${count}</th>
                                    <td>${snap.data().Initials} ${
										snap.data().Surname
									}</td>
                                    <td>${docs.data().StudentNo}</td>
                                    <td>${cz.data().Course}</td>
                                    <td>${docs.data().Status_Application}</td>
                                </tr>
									`;
								});
								html += div;
								list.innerHTML = html;
								document.querySelector(".apliedop").innerHTML =
									count;
							});
					});
			});
	}
}

function get(g) {
	console.log(g);
	displayPersonal(g);
	displayContact(g);
	displayNextofKinContact(g);
	displaySchool(g);
	displayTertiary(g);
	appliedCourses(g);
	uploadedDocuments(g);
	uploadedDocuments2(g);
	uploadedDocuments3(g);
	statusUpdatenow(g);
}

function statusUpdatenow(u) {
	console.log(u);
	document.getElementById("updateStatus").addEventListener("click", () => {
		var status = document.getElementById("statusUpd").value;

		db.collection("studentApplication")
			.doc(u)
			.collection("statusOfapplication")
			.doc("96325")
			.update({
				Status_Application: status,
			})
			.then(() => {
				if (status == "Accepted") {
					db.collection("studentApplication")
						.doc(u)
						.collection("statusOfapplication")
						.doc("96325")
						.get()
						.then((docss) => {
							db.collection("Admin")
								.doc("Admission")
								.collection("acceptedApplications")
								.add({
									ID: u,
									Email: docss.data().Email,
								})
								.then(() => {
									u = "";
									swal(
										"Successful",
										"Status updated",
										"success"
									);
									setTimeout(() => {
										window.location.reload();
									}, 5000);
								});
						});
				}

				if (
					status == "Rejected" ||
					status == "Rejected-space-constraint"
				) {
					db.collection("studentApplication")
						.doc(u)
						.collection("statusOfapplication")
						.doc("96325")
						.get()
						.then((docss) => {
							db.collection("Admin")
								.doc("Admission")
								.collection("rejecetdApplications")
								.add({
									ID: u,
									Email: docss.data().Email,
								})
								.then(() => {
									u = "";
									swal(
										"Successful",
										"Status updated",
										"success"
									);
									setTimeout(() => {
										window.location.reload();
									}, 5000);
								});
						});
				} else {
					swal("Successful", "Status updated", "success");
					setTimeout(() => {
						window.location.reload();
					}, 5000);
				}
			});
	});
}
function reload() {
	window.location.reload();
}
function displayPersonal(p) {
	db.collection("studentApplication")
		.doc(p)
		.collection("personalDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("RSAID").innerHTML =
				snaps.data().South_African;
			document.getElementById("dateOfBirth").innerHTML =
				snaps.data().Date_Of_Birth;
			document.getElementById("idNumber").innerHTML = snaps.data().RSA_ID;
			document.getElementById("gender").innerHTML = snaps.data().Gender;
			document.getElementById("title").innerHTML = snaps.data().Title;
			document.getElementById("initials").innerHTML =
				snaps.data().Initials;
			document.getElementById("surname").innerHTML = snaps.data().Surname;
			document.getElementById("name").innerHTML = snaps.data().Name;
			document.getElementById("language").innerHTML =
				snaps.data().Language;
			document.getElementById("race").innerHTML = snaps.data().Race;
			document.getElementById("NameInit").innerHTML = `<span>${
				snaps.data().Initials
			} ${snaps.data().Surname}</span>`;
		});
}

function displayContact(c) {
	db.collection("studentApplication")
		.doc(c)
		.collection("contactDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("email").innerHTML = snaps.data().Email;

			document.getElementById("cellphone").innerHTML =
				snaps.data().Cellphone;

			document.getElementById("address1").innerHTML =
				snaps.data().Physical_Address.address1;

			document.getElementById("address2").innerHTML =
				snaps.data().Physical_Address.address2;

			document.getElementById("address3").innerHTML =
				snaps.data().Physical_Address.address3;

			document.getElementById("address4").innerHTML =
				snaps.data().Physical_Address.address4;
			document.getElementById("addressSame").innerHTML =
				snaps.data().Status;
			document.getElementById("postal1").innerHTML =
				snaps.data().Postal_Address.postal1;
			document.getElementById("postal2").innerHTML =
				snaps.data().Postal_Address.postal2;

			document.getElementById("postal3").innerHTML =
				snaps.data().Postal_Address.postal3;

			document.getElementById("postal4").innerHTML =
				snaps.data().Postal_Address.postal4;
		});
}

function displayNextofKinContact(k) {
	db.collection("studentApplication")
		.doc(k)
		.collection("kinDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("name2").innerHTML = snaps.data().Name;
			document.getElementById("email2").innerHTML = snaps.data().Email;

			document.getElementById("cellphone2").innerHTML =
				snaps.data().Cellphone;

			document.getElementById("address11").innerHTML =
				snaps.data().Physical_Address.address1;

			document.getElementById("address22").innerHTML =
				snaps.data().Physical_Address.address2;

			document.getElementById("address33").innerHTML =
				snaps.data().Physical_Address.address3;

			document.getElementById("address44").innerHTML =
				snaps.data().Physical_Address.address4;
			document.getElementById("addressSame2").innerHTML =
				snaps.data().Status;
			document.getElementById("postal11").innerHTML =
				snaps.data().Postal_Address.postal1;
			document.getElementById("postal22").innerHTML =
				snaps.data().Postal_Address.postal2;

			document.getElementById("postal33").innerHTML =
				snaps.data().Postal_Address.postal3;

			document.getElementById("postal44").innerHTML =
				snaps.data().Postal_Address.postal4;
		});
}

function displaySchool(s) {
	let html = "";
	let div = "";
	const QuestionList = document.getElementById("list22");
	db.collection("studentApplication")
		.doc(s)
		.collection("schoolResults")
		.onSnapshot((docs) => {
			docs.forEach((element) => {
				div = `
                <tr>
                <td>${element.data().Subject}</td>
									<td>${element.data().Grade}</td>
									<td>${element.data().Level}</td>
									
                                    </tr>
                `;
				html += div;
				QuestionList.innerHTML = html;
			});
		});

	db.collection("studentApplication")
		.doc(s)
		.collection("schoolName")
		.doc("96325")
		.onSnapshot((doc) => {
			console.log(doc.data().Finished);
			document.querySelector("#grade12").innerHTML = doc.data().Finished;
			document.querySelector("#gradeinResults").innerHTML =
				doc.data().Grade;
			document.querySelector("#schoolName").innerHTML = doc.data().School;
			document.querySelector("#province").innerHTML = doc.data().Province;
		});
}

function displayTertiary(t) {
	db.collection("studentApplication")
		.doc(t)
		.collection("tertiaryDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("previousInst").innerHTML =
				snaps.data().Include_Tertiary_Results;
			document.getElementById("nameInst").innerHTML =
				snaps.data().Institution_Name;
			document.getElementById("currentActivity").innerHTML =
				snaps.data().Current_Activity;
		});
}

function appliedCourses(c) {
	db.collection("studentApplication")
		.doc(c)
		.collection("appliedCourses")
		.onSnapshot((docSnap) => {
			const listAll = document.getElementById("list2");
			var div = "";
			var html = "";
			var count = 0;
			docSnap.forEach((eleSnap) => {
				count++;
				div = `
                <tr>
                <th scope="row">${count}</th>
                <td>${eleSnap.data().Course_Code}</td>
                <td>${eleSnap.data().Course}</td>
                
               
                
            </tr>
                `;
				html += div;
				listAll.innerHTML = html;
			});
		});
}

function uploadedDocuments(id) {
	db.collection("studentApplication")
		.doc(id)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("ID")
		.doc("96325")
		.onSnapshot((idd) => {
			document.getElementById("preloadID").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${idd.data().File_name} </a> 
										
				`;
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${idd.data().File_name} </a> 
										
				`;
			} else {
			}
		});
}

function uploadedDocuments2(rs) {
	db.collection("studentApplication")
		.doc(rs)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("secondaryResults")
		.doc("96325")
		.onSnapshot((idd) => {
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID2").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${idd.data().File_name} </a> 
				`;
			} else {
			}
		});
}

function uploadedDocuments3(I) {
	db.collection("studentApplication")
		.doc(I)
		.collection("tertiaryDetails")
		.doc("96325")
		.get()
		.then((snap) => {
			var status = snap.data().Include_Tertiary_Results;
			console.log(status);
			if (status == "no") {
				document.getElementById("tert").classList.remove("active");
			} else {
				document.getElementById("tert").classList.add("active");
			}
		});

	db.collection("studentApplication")
		.doc(I)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("tertiaryTranscript")
		.doc("96325")
		.onSnapshot((idd) => {
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID3").innerHTML = `
            <a href="${idd.data().Url}" target= "_blank">${
					idd.data().File_name
				} </a> 
            `;
			} else {
			}
		});
}
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

	db.collection("Admin")
		.doc(annTitle)
		.collection("Announcements")
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
function registerStaff() {
	var Name = document.getElementById("name-staff").value;
	if (Name == "") {
		swal("Please enter Name!");
		return;
	}
	var Surname = document.getElementById("surname-staff").value;
	if (Surname == "") {
		swal("Please enter Surname!");
		return;
	}
	var Titlestaff = document.getElementById("title-staff").value;
	if (Titlestaff == "") {
		swal("Please enter your Title!");
		return;
	}
	var ID = document.getElementById("id-staff").value;
	if (ID == "") {
		swal("Please enter ID!");
		return;
	}
	if (ID.length != 13) {
		swal("Please make sure ID is 13 digits !");
		return false;
	}

	if (isNaN(ID)) {
		swal("Please enter numeric values!");
		return false;
	}
	var email = document.getElementById("emai-staff").value;
	if (email == "") {
		swal("Please enter email!");
		return;
	}
	var course = document.getElementById("course-staff").value;
	if (course == "") {
		swal("Please enter course!");
		return;
	}
	var password = document.getElementById("password-staff").value;
	if (password == "") {
		swal("Please create password!");
		return;
	}
	var ConfirmP = document.getElementById("confirmP-staff").value;
	if (ConfirmP == "") {
		swal("Please confirm password!");
		return;
	}

	if (password == ConfirmP) {
	}
	auth.createUserWithEmailAndPassword(email, password)
		.then(() => {
			db.collection("Staff")
				.doc(auth.currentUser.uid)
				.set(
					{
						Name: Name,
						Surname: Surname,
						Email: email,
						Course: course,
						ID_NO: ID,
						Title: Titlestaff,
						UserType: "Staff",
					},
					(merge = true)
				)
				.then(() => {
					swal("Successful", "Posted successfully", "success", {
						buttons: false,
						timer: 2000,
					});
					document.getElementById("name-staff").value = "";
					document.getElementById("confirmP-staff").value = "";
					document.getElementById("password-staff").value = "";
					document.getElementById("emai-staff").value = "";
					document.getElementById("id-staff").value = "";
					document.getElementById("surname-staff").value = "";
				});
		})
		.catch((error) => {
			alert(error);
		});
}
