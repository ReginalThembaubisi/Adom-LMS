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
			var nameUser = snaps.data().Name + " " + snaps.data().Surname;
			userName(nameUser);
		});
}
displayPersonal();
function process3() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess2.html?User=" + ID + "";
}
function displayContact() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
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
displayContact();
function process4() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess3.html?User=" + ID + "";
}

function displayNextofKinContact() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
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
displayNextofKinContact();
function process5() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess4.html?User=" + ID + "";
}

function displaySchool() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	let html = "";
	let div = "";
	const QuestionList = document.getElementById("list");
	db.collection("studentApplication")
		.doc(ID)
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
		.doc(ID)
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
displaySchool();
function process6() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess5.html?User=" + ID + "";
}

function displayTertiary() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
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
displayTertiary();
function process7() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess6.html?User=" + ID + "";
}

function appliedCourses() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
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
appliedCourses();
function process8() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess7.html?User=" + ID + "";
}

function uploadedDocuments() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("ID")
		.doc("96325")
		.onSnapshot((idd) => {
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${idd.data().File_name} </a> 
										
				`;
			} else {
			}
		});
}
uploadedDocuments();

function uploadedDocuments2() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
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
uploadedDocuments2();

function uploadedDocuments3() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
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
}
uploadedDocuments3();
function userName(n) {
	console.log(n);
	const formStudent = document.getElementById("generate-student");
	formStudent.addEventListener("submit", (e) => {
		e.preventDefault();
		const query = window.location.search;
		const url = new URLSearchParams(query);
		const ID = url.get("User");

		var lastSTDNo = "";
		var password = document.querySelector(".pin1").value;
		if (password == "") {
			swal("Please create password!");
			return;
		}
		var Confirmpassword = document.querySelector(".pin2").value;
		if (Confirmpassword == "") {
			swal("Please confirm password!");
			return;
		}
		if (password == Confirmpassword) {
			db.collection("studentApplication")
				.doc(ID)
				.collection("contactDetails")
				.doc("96325")
				.get()
				.then((snap0) => {
					var email = snap0.data().Email;

					auth.createUserWithEmailAndPassword(email, password).then(
						() => {
							db.collection("Admin")
								.doc("lastStudentNo")
								.get()
								.then((doc) => {
									lastSTDNo = doc.data().StudentNo;
									if (lastSTDNo != "") {
										lastSTDNo++;
									}
								})
								.then(() => {
									var today = new Date();
									var time = today.getTime();

									db.collection("studentApplication")
										.doc(ID)
										.collection("statusOfapplication")
										.doc("96325")
										.set({
											Status_Application: "Submitted",
											StudentNo: lastSTDNo,
											Password: password,
											Email: email,
											Date: time,
										})
										.then(() => {
											db.collection("Admin")
												.doc("lastStudentNo")
												.set({
													StudentNo: lastSTDNo,
												})
												.then(() => {
													Email.send({
														Host: "smtp.gmail.com",
														Username:
															"chris.adomtech@gmail.com",
														Password:
															"rznrvvqwumcyanvo",
														To: "chris.adomtech@gmail.com",
														From: email,
														Subject: `${n} Submitted Application to study at Adom`,
														Body: `<p style="font-weight:bold;">From: ${n}</p><br />Student No. : ${lastSTDNo}<br /> Status: Submitted Application<p>Login Details<br /> Username: ${email} <br />Password: ${password}</p>  <br /> <p>Kind Regards<br />${n} `,
													}).then((message) => {
														db.collection("Admin")
															.doc("Admission")
															.collection(
																"submittedApplications"
															)
															.add({
																ID: ID,
																Email: email,
															})
															.then(() => {
																Email.send({
																	Host: "smtp.gmail.com",
																	Username:
																		"chris.adomtech@gmail.com",
																	Password:
																		"rznrvvqwumcyanvo",
																	To: [email],
																	From: "chris.adomtech@gmail.com",
																	Subject: `Adom Technologies sent you a message`,
																	Body: `<p style="font-weight:bold;">From: Adom Technologies</p><br /><p>Your Application to Study at Adom Technology has been Submitted</p><p>Login Details<br /> Username: ${email} <br />Password: ${password}</p> <p>We will contact you as soon as we finish processing your Application</p>  <br /> <p>Kind Regards<br />Adom Technologies `,
																}).then(
																	(
																		message
																	) => {
																		window.location.href =
																			window.location.href =
																				"applicationProcess10.html?User=" +
																				ID +
																				"";
																	}
																);
															});
													});
												});
										});
								});
						}
					);
				});
		} else {
			swal("Password dont match!");
			return;
		}
	});
}
