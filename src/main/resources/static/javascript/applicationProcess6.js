var y = "yes";
var n = "no";
var chosed = "";
function option() {
	setTimeout(() => {
		var answer1 = document.getElementById("previousInst").value;
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
	document.getElementById("passed").classList.add("active");
	chosed = "yes";
	option();
}
function no() {
	document.getElementById("passed").classList.remove("active");
	option();
}
function process7() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	var institutionName = "";
	var includeTertiaryResults = document.getElementById("previousInst").value;
	if (includeTertiaryResults == "") {
		swal("Please choose if you want to include tertiary results!");
		return;
	}

	if (chosed == "yes") {
		institutionName = document.getElementById("nameInst").value;

		if (institutionName == "") {
			swal("Please enter Institute Name before you can proceed!");
			return;
		}
	}
	var currentActivity = document.getElementById("currentActivity").value;
	if (currentActivity == "") {
		swal("Please choose current activity before next year!");
		return;
	}

	db.collection("studentApplication")
		.doc(ID)
		.collection("tertiaryDetails")
		.doc("96325")
		.set({
			Include_Tertiary_Results: includeTertiaryResults,
			Institution_Name: institutionName,
			Current_Activity: currentActivity,
		})
		.then(() => {
			window.location.href = "applicationProcess7.html?User=" + ID + "";
		});
}
function display() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("tertiaryDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("previousInst").value =
				snaps.data().Include_Tertiary_Results;
			document.getElementById("nameInst").value =
				snaps.data().Institution_Name;
			document.getElementById("currentActivity").value =
				snaps.data().Current_Activity;
		});
}
display();
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess5.html?User=" + ID + "";
}
