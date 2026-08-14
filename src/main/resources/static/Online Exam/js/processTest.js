let userkey = "";
var onWhichQuestion = "";
var howLong = 0;
var Namepro = "";
var SurnamePro = "";
var EmailPro = "";
var CellPhonePro = "";
function Start() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("personalDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			Namepro = snaps.data().Name;
			SurnamePro = snaps.data().Surname;

			db.collection("studentApplication")
				.doc(ID)
				.collection("contactDetails")
				.doc("96325")
				.get()
				.then((snaps) => {
					CellPhonePro = snaps.data().Cellphone;
					EmailPro = snaps.data().Email;

					db.collection("EnrolledCourses")
						.doc("Systems Development")
						.collection("Test")
						.doc("96325")
						.collection("UsersTookTest")
						.add({
							Email: EmailPro,
							Name: Namepro,
							Surname: SurnamePro,
							Cellnumber: CellPhonePro,
							AnsweredQuestion: 0,
						})
						.then(() => {
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.where("Email", "==", EmailPro)
								.get()
								.then((snaps) => {
									snaps.forEach((doc) => {
										getUser(doc.id);
									});
								});
						});
				});
		});
}
function getUser(user) {
	console.log(user);
	window.location.href = "question.html?User=" + user + "";
}
function readyUser() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const vidkey = url.get("User");

	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection("Test")
		.doc("96325")
		.collection("UsersTookTest")
		.doc(vidkey)
		.get()
		.then((docUser) => {
			onWhichQuestion = docUser.data().AnsweredQuestion + 1;

			db.collection("EnrolledCourses")
				.doc("Systems Development")
				.collection("Test")
				.doc("96325")
				.onSnapshot((sn) => {
					if (
						docUser.data().AnsweredQuestion ==
						sn.data().totalquestion
					) {
						location.href = "submitted.html?User=" + vidkey + "";
					} else {
						db.collection("EnrolledCourses")
							.doc("Systems Development")
							.collection("Test")
							.doc("96325")
							.collection("Questions")
							.where("Question", "==", onWhichQuestion)
							.get()
							.then((snaps) => {
								snaps.forEach((doc) => {
									console.log(doc.data().Time);
									var duTime = doc.data().Time;

									howLong = duTime;
								});
								getQ(onWhichQuestion, howLong);
							});
					}
				});
		});

	userkey = vidkey;
}
function getQ(q, t) {
	console.log(t);

	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection("Test")
		.doc("96325")
		.collection("Questions")
		.where("Question", "==", q)
		.get()
		.then((snaps) => {
			let status = "block";
			var a = "";
			var b = "";
			var c = "";
			var d = "";
			var e = "";
			var T = "";
			var F = "";
			snaps.forEach((doc) => {
				if (doc.data().Long == null) {
					var statusL = "none";
				} else {
					statusL = "block";
				}
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
				console.log(doc.data().AskedQuestion);
				document.querySelector(
					"#parent"
				).innerHTML = `<div class="ass1 col-12">
                <div class="col-6">
                    <h6 style="font-weight: bold">Question ${
						doc.data().Question
					}</h6>
                </div>
                <div class="col-6 time"><h6 id="downTime"></h6></div>
            </div>
            <div class="question">
                <p>
                   ${doc.data().AskedQuestion}
                </p>
				
                <div  style="display:${statusL}">
                <textarea
                    name=""
                    id=""
                    cols="30"
                    rows="3"
                    placeholder="Write answer"
                    class="corret-text writtenAnswer"
                ></textarea>
            </div>
                <div class="choose"style="display:${status}">
                    <input
                        type="checkbox"
                        id="${a}"
                        name="${a}"
                        value="${a}"
						class="optionClicked"
                    />
                    <label for="vehicle1"> ${a}</label><br />
                    <input
                        type="checkbox"
                        id="${b}"
                        name="${b}"
                        value="${b}"
						class="optionClicked"
                    />
                    <label for="vehicle1"> ${b}</label><br />
                    <input
                        type="checkbox"
                        id="${c}"
                        name="${c}"
                        value="${c}"
						class="optionClicked"
                    />
                    <label for="vehicle1"> ${c}</label><br />
                    <input
                        type="checkbox"
                        id="${d}"
                        name="${d}"
                        value="${d}"
						class="optionClicked"
                    />
                    <label for="vehicle2"> ${d}</label><br />
                    <input
                        type="checkbox"
                        id="${e}"
                        name="${e}"
                        value="${e}"
						class="optionClicked"
                    />
                    <label for="vehicle3"> ${e}t</label
                    ><br /><br />
                </div>
                <div id="rtrfrfr"  style="display:${statusT}">
							<p style="font-size:small">True or False</p>
							<div class="col-md-2 ">
								<input
									type="checkbox"
									id="${T}"
									name="${T}"
									value="${T}"
									class="truth"
								/>
								<label for="vehicle3"> ${T}</label><br />
								<input
									type="checkbox"
									id="${F}"
									name="${F}"
									value="${F}"
									class="truth"
								/>
								<label for="vehicle3"> ${F}</label><br />
							</div>
            </div>
			<span>${doc.data().MarkAllocation} marks</span>`;
				let startingMinutes = t;
				let givenTime = startingMinutes * 60;

				setInterval(tim, 1000);
				function tim() {
					var duraInBrowser = 0;
					let minutes = Math.floor(givenTime / 60);
					let seconds = givenTime % 60;

					duraInBrowser = minutes + ":" + seconds;
					console.log(duraInBrowser);
					if (seconds < 0) {
						document.getElementById("downTime").innerHTML = "";
						db.collection("EnrolledCourses")
							.doc("Systems Development")
							.collection("Test")
							.doc("96325")
							.collection("UsersTookTest")
							.doc(userkey)
							.update(
								{
									AnsweredQuestion: onWhichQuestion,
								},
								(merge = true)
							);
						setTimeout(function () {
							location.reload();
						}, 1600);
					} else {
						document.getElementById("downTime").innerHTML =
							"Time remaining: " + duraInBrowser + " min";
					}

					givenTime--;
				}
				if (statusL == "block") {
					var btnAdd = document.querySelector(".addToUser");
					btnAdd.addEventListener("click", () => {
						var getUserAnswer =
							document.querySelector(".writtenAnswer").value;
						if (getUserAnswer != "") {
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.doc(userkey)
								.collection("Test")
								.add({
									Question: onWhichQuestion,
									AskedQuestion: doc.data().AskedQuestion,
									Long: "yes",
									RespondedOption: null,
									OptionsT: null,
									Response: getUserAnswer,
									CorrectAnswer: doc.data().Answer,
									UserMark: "Needs marking",
									QuestionMark: doc.data().MarkAllocation,
								});
						} else {
							console.log("No reponse recorded");
							getUserAnswer = "No reponse recorded";
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.doc(userkey)
								.collection("Test")
								.add({
									Question: onWhichQuestion,
									AskedQuestion: doc.data().AskedQuestion,
									Long: "yes",
									RespondedOption: null,
									OptionsT: null,
									Response: getUserAnswer,
									CorrectAnswer: doc.data().Answer,
									UserMark: "No answer given",
									QuestionMark: doc.data().MarkAllocation,
								});
						}
					});
				}

				if (status == "block") {
					var btnAdd = document.querySelector(".addToUser");
					btnAdd.addEventListener("click", () => {
						var getUserAnswerOption =
							document.getElementsByClassName("optionClicked");
						var str = "";
						for (i = 0; i < 5; i++) {
							if (getUserAnswerOption[i].checked === true) {
								str += getUserAnswerOption[i].value;
							}
						}
						if (str != "") {
							if (str == doc.data().Answer) {
								var markObtained = doc.data().MarkAllocation;
							} else {
								markObtained = 0;
							}
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.doc(userkey)
								.collection("Test")
								.add({
									Question: onWhichQuestion,
									AskedQuestion: doc.data().AskedQuestion,
									Long: null,
									RespondedOption: str,
									OptionsT: null,
									Response: null,
									CorrectAnswer: doc.data().Answer,
									UserMark: markObtained,
									QuestionMark: doc.data().MarkAllocation,
								});
						} else {
							console.log("No reponse recorded");
							str = "No reponse recorded";
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.doc(userkey)
								.collection("Test")
								.add({
									Question: onWhichQuestion,
									AskedQuestion: doc.data().AskedQuestion,
									Long: null,
									RespondedOption: str,
									OptionsT: null,
									Response: null,
									CorrectAnswer: doc.data().Answer,
									UserMark: "No answer given",
									QuestionMark: doc.data().MarkAllocation,
								});
						}
						return false;
					});
				}

				if (statusT == "block") {
					var btnAdd = document.querySelector(".addToUser");
					btnAdd.addEventListener("click", () => {
						var AnswerOptionT =
							document.getElementsByClassName("truth");
						var strT = "";
						for (i = 0; i < 2; i++) {
							if (AnswerOptionT[i].checked === true) {
								strT += AnswerOptionT[i].value;
							}
						}
						if (strT != "") {
							if (strT == doc.data().Answer) {
								var markObtained = doc.data().MarkAllocation;
							} else {
								markObtained = 0;
							}
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.doc(userkey)
								.collection("Test")
								.add({
									Question: onWhichQuestion,
									AskedQuestion: doc.data().AskedQuestion,
									Long: null,
									RespondedOption: null,
									OptionsT: strT,
									Response: null,
									CorrectAnswer: doc.data().Answer,
									UserMark: markObtained,
									QuestionMark: doc.data().MarkAllocation,
								});
						} else {
							console.log("No reponse recorded");
							strT = "No reponse recorded";
							db.collection("EnrolledCourses")
								.doc("Systems Development")
								.collection("Test")
								.doc("96325")
								.collection("UsersTookTest")
								.doc(userkey)
								.collection("Test")
								.add({
									Question: onWhichQuestion,
									AskedQuestion: doc.data().AskedQuestion,
									Long: null,
									RespondedOption: null,
									OptionsT: strT,
									Response: null,
									CorrectAnswer: doc.data().Answer,
									UserMark: "No answer given",
									QuestionMark: doc.data().MarkAllocation,
								});
						}
						return false;
					});
				}
			});
		});
	onTimeSet1();
}
function next() {
	var a = 1600;
	db.collection("EnrolledCourses")
		.doc("Systems Development")
		.collection("Test")
		.doc("96325")
		.collection("UsersTookTest")
		.doc(userkey)
		.update(
			{
				AnsweredQuestion: onWhichQuestion,
			},
			(merge = true)
		);
	setTimeout(function () {
		location.reload();
	}, a);
}
function onTimeSet1() {
	var setTime = false;
	if (setTime == true) {
		var a = 20600;
		db.collection("EnrolledCourses")
			.doc("Systems Development")
			.collection("Test")
			.doc("96325")
			.collection("UsersTookTest")
			.doc(userkey)
			.update(
				{
					AnsweredQuestion: onWhichQuestion,
				},
				(merge = true)
			);
		setTimeout(function () {
			location.reload();
		}, a);
	} else {
	}
}
function finished() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const vidkey = url.get("User");

	document.getElementById("exit_button").addEventListener("click", () => {});
}
finished();
