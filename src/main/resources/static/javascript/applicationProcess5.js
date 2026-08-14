var y = "yes";
var n = "no";
var chosed = false;
function option() {
	setTimeout(() => {
		var answer1 = document.getElementById("grade12").value;
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
			document.getElementById("gradeinResults").value = 12;
		}
		if (a == n) {
			no();
			document.getElementById("gradeinResults").value =
				document.getElementById("currentGrade").value;
		}
	}
}
function yes() {
	document.getElementById("passed").classList.remove("active");
	option();
}
function no() {
	document.getElementById("passed").classList.add("active");
	option();
	chosed = true;
}

var addSubject = document.getElementById("addSubjects");

function add() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	addSubject.addEventListener("click", () => {
		var school = document.querySelector(".nameS").value;
		var schoolP = document.querySelector(".nameP").value;
		var grade = document.getElementById("gradeinResults").value;

		if (school !== "" && schoolP !== "") {
			if (grade == "") {
				swal(
					"Please select if you finished grade 12 or Select grade you are currently in!"
				);
			} else {
				var selectedSubject = document.getElementById("subject").value;
				if (selectedSubject == "") {
					swal("Please select Subject!");

					return;
				}

				var level = document.getElementById("subjectLevel").value;
				if (level == "") {
					swal("Please select Level!");

					return;
				}

				db.collection("studentApplication")
					.doc(ID)
					.collection("schoolResults")
					.add({
						Subject: selectedSubject,
						Grade: grade,
						Level: level,
					})
					.then(() => {
						swal("Successful", "Subject Added", "success");
						document.getElementById("subject").value = "";
						document.getElementById("subjectLevel").value = "";
						totalSubjects();
					});
			}
		} else {
			swal("Please enter your School Name & Province!");
		}
	});
}
var count = -1;
function totalSubjects() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	let html = "";
	let div = "";
	count++;
	console.log(count);
	if (count == 1) {
		var schoolName = document.getElementById("schoolName").value;
		var schoolProvince = document.getElementById("province").value;
		var status = document.querySelector(".finishedOr").value;
		var currentGrade = document.querySelector(".cuG").value;

		db.collection("studentApplication")
			.doc(ID)
			.collection("schoolName")
			.doc("96325")
			.set({
				School: schoolName,
				Province: schoolProvince,
				Finished: status,
				Grade: currentGrade,
			});
	}

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
									<td>
										<span class="edit" onclick="remove('${element.id}')"
											><i class="fas fa-minus-circle"></i>
											Remove</span
										>
									</td>
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
			document.querySelector(".getit").value = doc.data().Finished;
			document.querySelector(".getit2").value = doc.data().Grade;
			document.querySelector(".getit3").value = doc.data().School;
			document.querySelector(".getit4").value = doc.data().Province;
		});
}
function remove(subjectkey) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("schoolResults")
		.doc(subjectkey)
		.delete();

	totalSubjects();
	swal("Successful", "Subject Deleted (If not reload page)", "success");
}
totalSubjects();
function process6() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess6.html?User=" + ID + "";
}
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess4.html?User=" + ID + "";
}
