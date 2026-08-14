var total = 0;
var markTotal = 0;
var duration = 0;
var whichCourse = "";
var passCourse = "";
var userClicked = "";
var testSelected = "";
function userCom() {
	window.location.href = "processTest.html";
}
function myFunction() {
	testSelected = document.getElementById("getTest").value;
	console.log(testSelected);

	enrolledCourse();
}
function CreateQuestion() {
	var entry = document.getElementsByClassName("checks");
	var getTest = document.getElementById("getTest").value;
	if (getTest == "") {
		alert("Select Test before creating");
		return false;
	}
	var str = "";
	for (i = 0; i < 3; i++) {
		if (entry[i].checked === true) {
			str += entry[i].value;
		}
	}
	if (str == "multiple") {
		window.location.href = "nowcreat.html?User=" + getTest + "";
	}
	if (str == "long") {
		window.location.href = "long.html?User=" + getTest + "";
	}
	if (str == "trueline") {
		window.location.href = "trueline.html?User=" + getTest + "";
	}
	if (str == "") {
		alert("choose one option before creating a question");
	}
}
function back() {
	window.location.href = "admin.html";
}
function enrolledCourse() {
	auth.onAuthStateChanged((user) => {
		if (user) {
			db.collection("Staff")
				.doc(auth.currentUser.uid)
				.get()
				.then((snaps) => {
					console.log(snaps.data().Course);

					whichCourse = snaps.data().Course;
					testDisply(whichCourse);
				});
		}
	});
}

function testDisply(whichCourse) {
	let html = "";
	let div = "";

	const QuestionList = document.querySelector("#main");
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.onSnapshot((snapdoc) => {
			document.getElementById("all-marks").innerHTML =
				snapdoc.data().SumMarks;
			document.getElementById("all-question").innerHTML =
				snapdoc.data().totalquestion;
		});
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.onSnapshot((sn) => {
			document.querySelector(".questionn").innerHTML =
				sn.data().totalquestion;
		});
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.onSnapshot((sn) => {
			document.querySelector(".tdtd").innerHTML = sn.data().Time;
		});
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.collection("Questions")
		.orderBy("Question", "asc")
		.onSnapshot((snaps) => {
			let status = "block";
			var a = "";
			var b = "";
			var c = "";
			var d = "";
			var e = "";
			var T = "";
			var F = "";
			snaps.forEach((doc) => {
				if (doc.data().Options == null) {
					status = "none";
				} else {
					status = "block";
					a = doc.data().Options[0];
					b = doc.data().Options[1];
					c = doc.data().Options[2];
					d = doc.data().Options[3];
					e = doc.data().Options[4];
				}
				if (doc.data().OptionsT == null) {
					var statusT = "none";
				} else {
					statusT = "block";
					var strTF = doc.data().OptionsT.split(",");
					T = strTF[0];
					F = strTF[1];
				}
				div = `
		<h5>Question ${doc.data().Question}</h5>
						<div class="questionContent">
							<br />
							<h6>${doc.data().AskedQuestion}</h6>
							
							
							<div class="inputoptions col-md-6 mt-3" id="myDIV" style="display:${status}">
							<input
								type="checkbox"
								id="vehicle1"
								name="vehicle1"
								value="Bike"
							/>
							<label for="vehicle1">
								${a}</label
							><br />
							<input
								type="checkbox"
								id="vehicle1"
								name="vehicle1"
								value="Bike"
							/>
							<label for="vehicle1">
							${b}</label
							><br />
							<input
								type="checkbox"
								id="vehicle1"
								name="vehicle1"
								value="Bike"
							/>
							<label for="vehicle1">
							${c}</label
							><br />
							<input
								type="checkbox"
								id="vehicle2"
								name="vehicle2"
								value="Car"
							/>
							<label for="vehicle2">
							${d}</label
							><br />
							<input
								type="checkbox"
								id="vehicle3"
								name="vehicle3"
								value="Boat"
							/>
							<label for="vehicle3">
							${e}</label
							><br />
							</div>
							<div id="rtrfrfr"  style="display:${statusT}">
							<p>True or False</p>
							<div class="col-md-2 getTrueFalse">
								<input
									type="checkbox"
									id="vehicle3"
									name="vehicle3"
									value="Boat"
								/>
								<label for="vehicle3"> ${T}</label><br />
								<input
									type="checkbox"
									id="vehicle3"
									name="vehicle3"
									value="Boat"
								/>
								<label for="vehicle3"> ${F}</label><br />
							</div>
							</div>
							<p style="color: green; margin-top: 10px">
								Correct Answer
							</p>
							<span style="color: rebeccapurple;">${doc.data().Answer}</span><br />
							<br />
							<div class="col-12 text-right points">
							<span>${doc.data().MarkAllocation} marks</span>
							</div>
							<hr>
							`;

				html += div;
				QuestionList.innerHTML = html;
			});
		});
}
function hideReveal() {}

function loadTotal() {
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.get()
		.then((spanshop) => {
			total = spanshop.data().totalquestion + 1;
			markTotal = spanshop.data().SumMarks;
			duration = spanshop.data().Time;
			addlong(total, markTotal, duration);
		});
}

function loadTotalMultiple() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const userkey = url.get("User");

	testSelected = userkey;
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.get()
		.then((spanshop) => {
			if (spanshop.data() == null) {
				db.collection("EnrolledCourses")
					.doc(whichCourse)
					.collection(testSelected)
					.doc("96325")
					.set(
						{
							totalquestion: 0,
							SumMarks: 0,
							Time: 0,
						},
						(merge = true)
					)
					.then(() => {
						db.collection("EnrolledCourses")
							.doc(whichCourse)
							.collection(testSelected)
							.doc("96325")
							.get()
							.then((spanshop) => {
								total = spanshop.data().totalquestion + 1;
								markTotal = spanshop.data().SumMarks;
								duration = spanshop.data().Time;
								console.log(markTotal);
								addMultiple(total, markTotal, duration);
							});
					});
			} else {
				if (spanshop.data() != null) {
					total = spanshop.data().totalquestion + 1;
					markTotal = spanshop.data().SumMarks;
					duration = spanshop.data().Time;
					console.log(markTotal);
					addMultiple(total, markTotal, duration);
				}
			}
		});
}

function loadTotalTrueline() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const userkey = url.get("User");

	testSelected = userkey;
	db.collection("EnrolledCourses")
		.doc(whichCourse)
		.collection(testSelected)
		.doc("96325")
		.get()
		.then((spanshop) => {
			console.log(spanshop.data());
			if (spanshop.data() == null) {
				db.collection("EnrolledCourses")
					.doc(whichCourse)
					.collection(testSelected)
					.doc("96325")
					.set(
						{
							totalquestion: 0,
							SumMarks: 0,
							Time: 0,
						},
						(merge = true)
					)
					.then(() => {
						db.collection("EnrolledCourses")
							.doc(whichCourse)
							.collection(testSelected)
							.doc("96325")
							.get()
							.then((spanshop) => {
								total = spanshop.data().totalquestion + 1;
								markTotal = spanshop.data().SumMarks;
								duration = spanshop.data().Time;
								console.log(markTotal);
								Truth(total, markTotal, duration);
							});
					});
			} else {
				if (spanshop.data() != null) {
					total = spanshop.data().totalquestion + 1;
					markTotal = spanshop.data().SumMarks;
					duration = spanshop.data().Time;
					console.log(markTotal);
					Truth(total, markTotal, duration);
				}
			}
		});
}
function addlong(sum, all, dura) {
	console.log(dura + "hh");
	var questionEntry = document.querySelector(".writtenQuestion").value;
	var CorrectAnswer = document.querySelector(".writtenAnswer").value;
	var markallocation = document.querySelector(".mark-allocation").value;
	var time = document.querySelector(".timeIn").value;
	var toDuration = parseInt(time) + dura;
	if (questionEntry != "") {
		db.collection("EnrolledCourses")
			.doc(whichCourse)
			.collection(testSelected)
			.doc("96325")
			.collection("Questions")
			.add({
				Question: sum,
				AskedQuestion: questionEntry,
				Long: "yes",
				Options: null,
				OptionsT: null,
				Answer: CorrectAnswer,
				Time: parseInt(time),
				MarkAllocation: markallocation,
			});
		var allmarks = all + parseInt(markallocation);
		db.collection("EnrolledCourses")
			.doc(whichCourse)
			.collection(testSelected)
			.doc("96325")
			.update(
				{
					totalquestion: sum,
					SumMarks: allmarks,
					Time: toDuration,
				},
				(merge = true)
			)
			.then(() => {
				alert("Question successfully Added");
				setTimeout(function () {
					location.href = "admin.html";
				}, 1600);
			});
	}
}
let arr = [];
function addMultiple(toti, num, duraMu) {
	console.log(duraMu);
	console.log(toti);
	console.log(num);
	var option1 = document.getElementsByClassName("option");
	var quesMult = document.querySelector(".mutliquestionair").value;
	var correct = document.querySelector(".correctOption").value;
	var mark = document.querySelector(".mult-mark-allocation").value;
	var time = document.querySelector(".timeIn").value;

	var toMU = parseInt(time) + duraMu;
	for (i = 0; i < 5; i++) {
		var a = i;

		arr[a] = option1[i].value;
	}
	console.log(arr);
	if (quesMult != "") {
		db.collection("EnrolledCourses")
			.doc(whichCourse)
			.collection(testSelected)
			.doc("96325")
			.collection("Questions")
			.add({
				Question: toti,
				AskedQuestion: quesMult,
				Long: null,
				Options: arr,
				OptionsT: null,
				Answer: correct,
				Time: parseInt(time),
				MarkAllocation: mark,
			});
		let num2 = parseInt(mark);
		let sumM = num + num2;
		console.log(sumM);
		db.collection("EnrolledCourses")
			.doc(whichCourse)
			.collection(testSelected)
			.doc("96325")
			.update(
				{
					totalquestion: toti,
					SumMarks: sumM,
					Time: toMU,
				},
				(merge = true)
			)
			.then(() => {
				alert("Question successfully Added");

				location.href = "admin.html";
			});
	}
}
function Truth(boo, oroo, TiT) {
	var trueAnswer = document.getElementsByClassName("boulins");
	var questionT = document.querySelector(".trueText").value;
	var marksGiven = document.querySelector(".markAllocated").value;
	var time = document.querySelector(".timeIn1").value;
	console.log(time);
	var allTT = parseInt(time) + TiT;
	var str = "";
	for (i = 0; i < 2; i++) {
		if (trueAnswer[i].checked === true) {
			str += trueAnswer[i].value;
		}
	}
	console.log(str);
	var btmarks = 0;
	if (questionT != "") {
		db.collection("EnrolledCourses")
			.doc(whichCourse)
			.collection(testSelected)
			.doc("96325")
			.collection("Questions")
			.add({
				Question: boo,
				AskedQuestion: questionT,
				Long: null,
				Options: null,
				OptionsT: "True,False",
				Answer: str,
				Time: parseInt(time),
				MarkAllocation: marksGiven,
			})
			.then(() => {
				btmarks = oroo + parseInt(marksGiven);
				db.collection("EnrolledCourses")
					.doc(whichCourse)
					.collection(testSelected)
					.doc("96325")
					.update(
						{
							totalquestion: boo,
							SumMarks: btmarks,
							Time: allTT,
						},
						(merge = true)
					);
				alert("Question successfully Added");

				location.href = "admin.html";
			});
	}
	return false;
}

function GetList() {
	var count = 0;
	let html = "";
	let div = "";

	const userList = document.querySelector("#usersDone");
	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection(testSelected)
		.doc("96325")
		.collection("UsersTookTest")
		.onSnapshot((users) => {
			users.forEach((doc) => {
				var name = doc.data().Name + " " + doc.data().Surname;
				count++;
				div = `<p onclick="displayUser('${doc.id}')">
			${name} 
		</p>`;
				html += div;
				userList.innerHTML = html;
			});
			document.querySelector(".counted").innerHTML = count;
		});
}
function displayUser(id) {
	window.location.href = "processTest.html?UserCon=" + id + "";
}

function loadUser() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const userkey = url.get("UserCon");
	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection(testSelected)
		.doc("96325")
		.collection("UsersTookTest")
		.doc(userkey)
		.get()
		.then((info) => {
			var name = info.data().Name + " " + info.data().Surname;
			var email = info.data().Email;
			var cell = info.data().Cellnumber;

			document.querySelector(".userViewName").innerHTML = `Name: ${name}`;
			document.querySelector(
				".userViewEmail"
			).innerHTML = `Email: ${email}`;
			document.querySelector(".userViewCell").innerHTML = `Cell: ${cell}`;
		});
	userClicked = userkey;
}
function respondofUser() {
	let html = "";
	let div = "";
	var count = 0;
	console.log(userClicked);
	const QuestionList = document.querySelector("#main2");
	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection(testSelected)
		.doc("96325")
		.collection("UsersTookTest")
		.doc(userClicked)
		.collection("Test")
		.orderBy("Question", "asc")
		.onSnapshot((snaps) => {
			let status = "block";

			snaps.forEach((doc) => {
				if (doc.data().Long == null) {
					var statusL = "none";
				} else {
					statusL = "block";
				}
				if (doc.data().RespondedOption == null) {
					status = "none";
				} else {
					status = "block";
				}
				if (doc.data().OptionsT == null) {
					var statusT = "none";
				} else {
					statusT = "block";
				}
				div = `
			<h5>Question ${doc.data().Question}</h5>
							<div class="questionContent">
								<br />
								<h6>${doc.data().AskedQuestion}</h6>
								<div  style="display:${statusL}">
								<span class="alnwer">Given Answer: ${doc.data().Response}</span>
							</div>
								
								<div class="inputoptions2 col-md-6 mt-3" id="myDIV" style="display:${status}">
								<span>Given Answer: ${doc.data().RespondedOption}</span>
								<br />
								</div>
								<div id="rtrfrfr"  style="display:${statusT}">
								<p>True or False</p>
								<div class="col-md-2 getTrueFalse2">
									<span>Given Answer: ${doc.data().OptionsT}</span>
								</div>
								</div>
								<p style="color: green; margin-top: 10px">
									Correct Answer
								</p>
								<span style="color: rebeccapurple;">${doc.data().CorrectAnswer}</span><br />
								<br />
								<div class="col-12 text-right points">
								<span>Scored: ${doc.data().UserMark} of ${doc.data().QuestionMark} marks</span>
								</div>
								<hr>
								`;
				count += parseInt(doc.data().UserMark);
				html += div;
				QuestionList.innerHTML = html;
			});
			db.collection("EnrolledCourses")
				.doc("Systems Development")
				.collection(testSelected)
				.doc("96325")
				.get()
				.then((info) => {
					var over = info.data().SumMarks;
					var Totalscore = (count / over) * 100;
					document.querySelector(".sc").innerHTML =
						Totalscore.toFixed(0) + "%";
				});
			console.log(count);
		});
}
function loadUser2() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const userkey = url.get("User");
	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection("Test")
		.doc("96325")
		.collection("UsersTookTest")
		.doc(userkey)
		.get()
		.then((info) => {
			var name = info.data().Name + " " + info.data().Surname;
			var email = info.data().Email;
			var cell = info.data().Cellnumber;

			document.querySelector(".userViewName").innerHTML = `Name: ${name}`;
			document.querySelector(
				".userViewEmail"
			).innerHTML = `Email: ${email}`;
			document.querySelector(".userViewCell").innerHTML = `Cell: ${cell}`;
		});
	userClicked = userkey;
}
function respondofUser2() {
	let html = "";
	let div = "";
	var count = 0;
	console.log(userClicked);
	const QuestionList = document.querySelector("#main2");
	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection("Test")
		.doc("96325")
		.collection("UsersTookTest")
		.doc(userClicked)
		.collection("Test")
		.orderBy("Question", "asc")
		.onSnapshot((snaps) => {
			let status = "block";

			snaps.forEach((doc) => {
				if (doc.data().Long == null) {
					var statusL = "none";
				} else {
					statusL = "block";
				}
				if (doc.data().RespondedOption == null) {
					status = "none";
				} else {
					status = "block";
				}
				if (doc.data().OptionsT == null) {
					var statusT = "none";
				} else {
					statusT = "block";
				}
				div = `
			<h5>Question ${doc.data().Question}</h5>
							<div class="questionContent">
								<br />
								<h6>${doc.data().AskedQuestion}</h6>
								<div  style="display:${statusL}">
								<span class="alnwer">Given Answer: ${doc.data().Response}</span>
							</div>
								
								<div class="inputoptions2 col-md-6 mt-3" id="myDIV" style="display:${status}">
								<span>Given Answer: ${doc.data().RespondedOption}</span>
								<br />
								</div>
								<div id="rtrfrfr"  style="display:${statusT}">
								<p>True or False</p>
								<div class="col-md-2 getTrueFalse2">
									<span>Given Answer: ${doc.data().OptionsT}</span>
								</div>
								</div>
								<p style="color: green; margin-top: 10px">
									Correct Answer
								</p>
								<span style="color: rebeccapurple;">${doc.data().CorrectAnswer}</span><br />
								<br />
								<div class="col-12 text-right points">
								<span>Scored: ${doc.data().UserMark} of ${doc.data().QuestionMark} marks</span>
								</div>
								<hr>
								`;
				count += parseInt(doc.data().UserMark);
				html += div;
				QuestionList.innerHTML = html;
			});
			db.collection("EnrolledCourses")
				.doc("Systems Development")
				.collection("Test")
				.doc("96325")
				.get()
				.then((info) => {
					var over = info.data().SumMarks;
					var Totalscore = (count / over) * 100;
					document.querySelector(".sc").innerHTML =
						Totalscore.toFixed(0) + "%";
				});
			console.log(count);
		});
}
