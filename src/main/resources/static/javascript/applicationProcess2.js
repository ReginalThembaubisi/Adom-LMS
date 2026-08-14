var y = "yes";
var n = "no";
function option() {
	setTimeout(() => {
		var answer1 = document.getElementById("RSAID").value;
		selectedAnswer(answer1);
	}, 1000);
}
function selectedAnswer(a) {
	if (a == "") {
		console.log("no answer selected");

		option();
	} else {
		console.log("The answer is: " + a);

		if (a == y) {
			yes();
		}
		if (a == n) {
			no();
		}
	}
}
function yes() {
	document.getElementById("noID").classList.remove("active");
	document.getElementById("yesID").classList.add("active");

	option();
}
function no() {
	option();
	document.getElementById("noID").classList.add("active");
	document.getElementById("yesID").classList.remove("active");
}

function process3() {
	var citizen = document.getElementById("RSAID").value;
	if (citizen == "") {
		swal("Please select RSA citizen!");
		return false;
	}
	var ID = document.getElementById("idNumber").value;
	if (ID == "") {
		swal("Please enter your ID number!");
		return false;
	}

	if (ID.length != 13) {
		swal("Please make sure ID is 13 digits !");
		return false;
	}

	if (isNaN(ID)) {
		swal("Please enter numeric values!");
		return false;
	} else {
		var dataofB = 0;
		for (var i = 0; i < 6; i++) {
			dataofB += ID[i];
		}
		console.log(dataofB);
	}

	var dateBirth = document.getElementById("dateOfBirth").value;
	if (dateBirth == "") {
		swal("Please enter date of birth");
		return false;
	}
	var gender = document.getElementById("gender").value;
	if (gender == "") {
		swal("Please enter your Gender!");
		return false;
	}
	var title = document.getElementById("title").value;
	if (title == "") {
		swal("Please enter your Title!");
		return false;
	}
	var initials = document.getElementById("initials").value;
	if (initials == "") {
		swal("Please enter your Initials!");
		return false;
	}
	var surname = document.getElementById("surname").value;
	if (surname == "") {
		swal("Please enter your Surname!");
		return false;
	}
	var name = document.getElementById("name").value;
	if (name == "") {
		swal("Please enter your Name(s)!");
		return false;
	}
	var language = document.getElementById("language").value;
	if (language == "") {
		swal("Please enter your Language!");
		return false;
	}
	var race = document.getElementById("race").value;
	if (race == "") {
		swal("Please enter your Race!");
		return false;
	}
	var spinner = document.getElementById("overlay");
	spinner.style.display = "block";
	db.collection("studentApplication")
		.doc(ID)
		.collection("personalDetails")
		.doc("96325")
		.set({
			South_African: citizen,
			RSA_ID: ID,
			Date_Of_Birth: dateBirth,
			Gender: gender,
			Title: title,
			Initials: initials,
			Surname: surname,
			Name: name,
			Language: language,
			Race: race,
		})
		.then(() => {
			window.location.href = "applicationProcess3.html?User=" + ID + "";
		});
}
function display() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("personalDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("RSAID").value = snaps.data().South_African;
			document.getElementById("dateOfBirth").value =
				snaps.data().Date_Of_Birth;
			document.getElementById("idNumber").value = snaps.data().RSA_ID;
			document.getElementById("gender").value = snaps.data().Gender;
			document.getElementById("title").value = snaps.data().Title;
			document.getElementById("initials").value = snaps.data().Initials;
			document.getElementById("surname").value = snaps.data().Surname;
			document.getElementById("name").value = snaps.data().Name;
			document.getElementById("language").value = snaps.data().Language;
			document.getElementById("race").value = snaps.data().Race;
		});
}
display();
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess2.html?User=" + ID + "";
}

/*
db.collection("Admin")
		.doc("lastStudentNo")
		.get()
		.then((snaps) => {
			var studentNoLastUsed = snaps.data().StudentNo;

			if (studentNoLastUsed != "") {
				studentNoLastUsed++;
				db.collection("studentApplication")
					.add({
						Student: studentNoLastUsed,
					})
					.then(() => {
						db.collection("Admin")
							.doc("lastStudentNo")
							.set({
								StudentNo: studentNoLastUsed,
							})
							.then(() => {
								window.location.href =
									"applicationProcess2.html";
							})
							.catch(function (error) {
								console.log("Error getting document:", error);
							});
					});
			}
		});
*/
