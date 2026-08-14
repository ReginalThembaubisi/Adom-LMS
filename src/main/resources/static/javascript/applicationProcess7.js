function selectCourse() {
	setTimeout(() => {
		var selectedFaculty = document.getElementById("selectFaculty").value;
		selectedAnswer(selectedFaculty);
	}, 1000);
}
function selectedAnswer(a) {
	console.log(a);
	if (a == "") {
		console.log("no answer selected");

		selectCourse();
	} else {
		console.log("The answer is: " + a);

		db.collection("Admin")
			.doc("Admission")
			.collection("Courses")
			.where("Faculty", "==", a)
			.get()
			.then((querySnapshot) => {
				let col = [];

				var div = "";
				var html = "";
				const ListCourses = document.getElementById("selectCourse");
				querySnapshot.forEach((element) => {
					col[0] = element.data().Course_Code;
					col[1] = element.data().Course;
					div = `
                    <option value="${col}">${element.data().Course}</option>
                        `;
					html += div;
					ListCourses.innerHTML = html;
				});
			});

		function selectedCourse() {
			setTimeout(() => {
				var selectedCourse =
					document.getElementById("selectCourse").value;
				chosenCourse(selectedCourse);
			}, 1000);
		}

		function chosenCourse(c) {
			if (c == "") {
				console.log("No course selected");
				selectedCourse();
			} else {
				console.log("You selected this Course: " + c);
				var ans = c.split(",");
				document.getElementById("courseCode").value = ans[0];
			}
			selectedCourse();
		}
		selectedCourse();
	}
}
function add() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	var btn = document.getElementById("btnCourse");
	btn.addEventListener("click", () => {
		var fac = document.querySelector(".getit2").value;
		if (fac == "") {
			swal("Please Select which Faculty!");
			return;
		}

		var cour = document.querySelector(".getit3").value;

		if (cour == "") {
			swal("Please Select Course!");
			return;
		} else {
			var ans = cour.split(",");
		}

		var Ccour = document.querySelector(".getit4").value;

		db.collection("studentApplication")
			.doc(ID)
			.collection("appliedCourses")
			.add({
				Faculty: fac,
				Course: ans[1],
				Course_Code: Ccour,
			})
			.then(() => {
				swal("Successful", "Course added", "success");
				appliedCourses();
			});
	});
}
add();
function appliedCourses() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("appliedCourses")
		.onSnapshot((docSnap) => {
			const listAll = document.getElementById("list");
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
                <td>
                <span class="edit" onclick="remove('${eleSnap.id}')"
											><i class="fas fa-minus-circle"></i>
											Remove</span</td>
               
                
            </tr>
                `;
				html += div;
				listAll.innerHTML = html;
			});
		});
}
function remove(subjectkey) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("appliedCourses")
		.doc(subjectkey)
		.delete();

	appliedCourses();
	swal("Successful", "Subject Deleted (If not reload page)", "success");
}
appliedCourses();
function process7() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess8.html?User=" + ID + "";
}
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess6.html?User=" + ID + "";
}
