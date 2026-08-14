function appliedCourses() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("appliedCourses")
		.onSnapshot((docSnap) => {
			docSnap.forEach((eleSnap) => {
				eleSnap.data().Course_Code;

				document.getElementById("course").value = eleSnap.data().Course;
				document.getElementById("courseCode").value =
					eleSnap.data().Course_Code;
			});
		});
}
appliedCourses();
function processReg3() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("appliedCourses")
		.onSnapshot((docSnap) => {
			docSnap.forEach((eleSnap) => {
				eleSnap.data().Course_Code;

				regCourse1(eleSnap.data().Course);
			});
		});

	function regCourse1(n) {
		db.collection("studentApplication")
			.doc(ID)
			.collection("registeredCourses")
			.doc("96325")
			.set({
				registeredCourse: n,
			})
			.then(() => {
				window.location.href =
					"registrationProcess3.html?User=" + ID + "";
			});
	}
}
