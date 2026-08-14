var y = "yes";
var n = "no";
function option() {
	setTimeout(() => {
		var answer1 = document.getElementById("studentNo").value;
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
	document.getElementById("personal").classList.remove("active");
	document.getElementById("already-applied").classList.add("active");
	document.getElementById("footer").classList.remove("active");

	option();
}
function no() {
	option();
	document.getElementById("personal").classList.add("active");
	document.getElementById("already-applied").classList.remove("active");
	document.getElementById("footer").classList.add("active");
}

function process2() {
	var entry = document.getElementsByClassName("checks");
	var str = "";
	for (i = 0; i < 1; i++) {
		if (entry[i].checked === true) {
			var spinner = document.getElementById("overlay");
			spinner.style.display = "block";
			str += entry[i].value;

			window.location.href = "applicationProcess2.html";
		} else {
			swal("Please accept Terms and Conditions!");
		}
	}
}
