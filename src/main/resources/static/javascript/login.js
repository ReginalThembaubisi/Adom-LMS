function login() {
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
										"dashboard.html?User=" + ID + "";
								} else {
									window.location.href =
										"userStatus.html?User=" + ID + "";
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
