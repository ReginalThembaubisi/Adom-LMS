var spinner = document.getElementById("overlay");

window.addEventListener("load", function () {
	spinner.style.display = "none";
});

function login() {
	//	location.href = "../profile/profile.html";

	var entry = document.getElementsByClassName("checks");

	var str = "";
	for (i = 0; i < 3; i++) {
		if (entry[i].checked === true) {
			str += entry[i].value;
		}
	}
	if (str == "Student") {
		console.log("Choose student");
		loginstudent();
	}
	if (str == "Staff") {
		console.log("Choose staff");
		loginstaff();
	}
	if (str == "Admin") {
		console.log("Choose admin");
	}
	if (str == "") {
		alert("choose one option before proceeding");
	}
}
function loginstudent() {
	spinner.style.display = "block";
	var email = document.getElementById("username").value;
	if (email == "") {
		spinner.style.display = "none";
		swal("Please enter email address!");
		return false;
	}
	var Pword = document.getElementById("password").value;
	if (Pword == "") {
		spinner.style.display = "none";
		swal("Please enter password!");
		return false;
	}
	auth.signInWithEmailAndPassword(email, Pword)
		.then((cred) => {
			db.collection("Admin")
				.doc("Admission")
				.collection("submittedApplications")
				.where("Email", "==", email)
				.get()
				.then((eleSnaps) => {
					eleSnaps.forEach((element) => {
						var ID = element.data().ID;
						db.collection("studentApplication")
							.doc(ID)
							.collection("statusOfapplication")
							.doc("96325")
							.get()
							.then((srDoc) => {
								if (
									srDoc.data().Status_Application ==
									"Registered"
								) {
									window.location.href =
										"../dashboard.html?User=" + ID + "";
								} else {
									window.location.href =
										"../userStatus.html?User=" + ID + "";
								}
							});
					});
				});
		})
		.catch(function (error) {
			swal(
				"Error!",
				"Email or Password is wrong! or User not Registered! ",
				"error"
			);
			spinner.style.display = "none";
		});
}
function loginstaff() {
	spinner.style.display = "block";
	var email = document.getElementById("username").value;
	if (email == "") {
		spinner.style.display = "none";
		swal("Please enter email address!");
		return false;
	}
	var Pword = document.getElementById("password").value;
	if (Pword == "") {
		spinner.style.display = "none";
		swal("Please enter password!");
		return false;
	}
	auth.signInWithEmailAndPassword(email, Pword)
		.then((cred) => {
			db.collection("Staff")
				.doc(auth.currentUser.uid)
				.get()
				.then((eleSnaps) => {
					if (eleSnaps.data().UserType == "Staff") {
						window.location.href = "../lectureProfile.html";
					} else {
						swal(
							"Error!",
							"Email or Password is wrong! or User not Permitted to log in! ",
							"error"
						);
						spinner.style.display = "none";
					}
				})
				.catch((error) => {
					swal(
						"Error!",
						" User not Permitted to use site! ",
						"error"
					);
					spinner.style.display = "none";
				});
		})
		.catch(function (error) {
			swal(
				"Error!",
				error +
					" Email or Password is wrong! or User not Permitted to log in! ",
				"error"
			);
			spinner.style.display = "none";
		});
}

function sendPasswordReset() {
	const email = document.getElementById("emailVerification").value;
	// [START auth_send_password_reset]
	auth.sendPasswordResetEmail(email)
	  .then(() => {
		// Password reset email sent!
		// ..
		alert("Password Reset link sent")
	  })
	  .catch((error) => {
		var errorCode = error.code;
		var errorMessage = error.message;
		// ..
		console.log(errorCode)
		console.log(errorMessage)
	  });
	// [END auth_send_password_reset]
  }