function acceptedNews() {
	document.getElementById("popup-1").classList.toggle("active");
}
function displayPersonal() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("personalDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			console.log(snaps.data().RSA_ID);
			document.getElementById("idNumber").innerHTML = snaps.data().RSA_ID;

			document.getElementById("NameInit").innerHTML = `<span>${
				snaps.data().Initials
			} ${snaps.data().Surname}</span>`;
			document.getElementById("NameInit2").innerHTML = `<span>${
				snaps.data().Initials
			} ${snaps.data().Surname}</span>`;
		});
}
displayPersonal();
function displayStatus() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("statusOfapplication")
		.doc("96325")
		.get()
		.then((snaps) => {
			if (
				snaps.data().Status_Application == "ID-Doc-Outstanding" ||
				snaps.data().Status_Application ==
					"Recent-Results-Doc-Outstanding" ||
				snaps.data().Status_Application ==
					"Tertiary-Transcript-Outstanding"
			) {
				document.getElementById("miss").classList.add("active");

				if (snaps.data().Status_Application == "ID-Doc-Outstanding") {
					document.getElementById("id-doc").classList.add("active");
					document
						.getElementById("uploadID-doc")
						.addEventListener("click", () => {
							const ref = firebase.storage().ref("IDs/");
							const file = document.querySelector("#ID").files[0];

							if (file == "") {
								swal("Please Select ID before you can Upload!");
								return;
							}
							var fname = "ID document";
							var today = new Date();
							var time = today.getTime();

							const name = +new Date() + "-" + file.name;
							const metadata = {
								contentType: file.type,
							};

							const task = ref.child(name).put(file, metadata);
							task.on(
								"state_changed",
								function progress(snapshot) {
									var percentage =
										(snapshot.bytesTransferred /
											snapshot.totalBytes) *
										100;
									document.getElementById("uploader").value =
										percentage;
									document.getElementById(
										"UpProgress"
									).innerHTML =
										"Upload " + percentage.toFixed(0) + "%";
								}
							);

							task.then((snapshot) =>
								snapshot.ref.getDownloadURL()
							)
								.then((url) => {
									console.log(url);

									db.collection("studentApplication")
										.doc(ID)
										.collection("uploadedDocuments")
										.doc("96325")
										.collection("ID")
										.doc("96325")
										.set({
											File_name: fname,
											Time: time,
											Url: url,
										})
										.then(() => {
											db.collection("studentApplication")

												.doc(ID)
												.collection(
													"statusOfapplication"
												)
												.doc("96325")
												.update({
													Status_Application:
														"Uploaded-ID-Doc",
												})
												.then(() => {
													swal(
														"Successful",
														"ID added",
														"success"
													);
													setTimeout(() => {
														location.reload();
													}, 5000);
												});
										});
								})
								.catch(console.error);
						});
				}
				if (
					snaps.data().Status_Application ==
					"Recent-Results-Doc-Outstanding"
				) {
					document.getElementById("sec-doc").classList.add("active");
					document
						.getElementById("secResults")
						.addEventListener("click", () => {
							const ref = firebase
								.storage()
								.ref("Recent Results/");
							const file =
								document.querySelector("#Recent-results")
									.files[0];

							if (file == "") {
								swal(
									"Please Select Results before you can Upload!"
								);
								return;
							}
							var fname = "Secondary Results document";
							var today = new Date();
							var time = today.getTime();

							const name = +new Date() + "-" + file.name;
							const metadata = {
								contentType: file.type,
							};

							const task = ref.child(name).put(file, metadata);
							task.on(
								"state_changed",
								function progress(snapshot) {
									var percentage =
										(snapshot.bytesTransferred /
											snapshot.totalBytes) *
										100;
									document.getElementById("uploader2").value =
										percentage;
									document.getElementById(
										"UpProgress2"
									).innerHTML =
										"Upload " + percentage.toFixed(0) + "%";
								}
							);

							task.then((snapshot) =>
								snapshot.ref.getDownloadURL()
							)
								.then((url) => {
									console.log(url);

									db.collection("studentApplication")
										.doc(ID)
										.collection("uploadedDocuments")
										.doc("96325")
										.collection("secondaryResults")
										.doc("96325")
										.set({
											File_name: fname,
											Time: time,
											Url: url,
										})
										.then(() => {
											db.collection("studentApplication")

												.doc(ID)
												.collection(
													"statusOfapplication"
												)
												.doc("96325")
												.update({
													Status_Application:
														"Uploaded-Recent-Results",
												})
												.then(() => {
													swal(
														"Successful",
														"Results added",
														"success"
													);
													setTimeout(() => {
														location.reload();
													}, 5000);
												});
										});
								})
								.catch(console.error);
						});
				}
				if (
					snaps.data().Status_Application ==
					"Tertiary-Transcript-Outstanding"
				) {
					document.getElementById("tert2").classList.add("active");

					var btnTertiaryUpload =
						document.getElementById("tertiaryBTN");
					btnTertiaryUpload.addEventListener("click", () => {
						const ref = firebase
							.storage()
							.ref("Tertiary Transcript/");
						const file = document.querySelector(
							"#transcript-results"
						).files[0];

						if (file == "") {
							swal(
								"Please Select Transcripts before you can Upload!"
							);
							return;
						}
						var fname = "Transcript document";
						var today = new Date();
						var time = today.getTime();

						const name = +new Date() + "-" + file.name;
						const metadata = {
							contentType: file.type,
						};

						const task = ref.child(name).put(file, metadata);
						task.on("state_changed", function progress(snapshot) {
							var percentage =
								(snapshot.bytesTransferred /
									snapshot.totalBytes) *
								100;
							document.getElementById("uploader3").value =
								percentage;
							document.getElementById("UpProgress3").innerHTML =
								"Upload " + percentage.toFixed(0) + "%";
						});

						task.then((snapshot) => snapshot.ref.getDownloadURL())
							.then((url) => {
								console.log(url);

								db.collection("studentApplication")
									.doc(ID)
									.collection("uploadedDocuments")
									.doc("96325")
									.collection("tertiaryTranscript")
									.doc("96325")
									.set({
										File_name: fname,
										Time: time,
										Url: url,
									})
									.then(() => {
										db.collection("studentApplication")

											.doc(ID)
											.collection("statusOfapplication")
											.doc("96325")
											.update({
												Status_Application:
													"Uploaded-Tertiary-Transcript",
											})
											.then(() => {
												swal(
													"Successful",
													"Tertiary Transcript added",
													"success"
												);
												setTimeout(() => {
													location.reload();
												}, 5000);
											});
									});
							})
							.catch(console.error);
					});
				}
			}
			if (
				snaps.data().Status_Application == "Accepted" ||
				snaps.data().Status_Application == "Temporaly_Registered"
			) {
				console.log(snaps.data().Status_Application);
				document.getElementById("registerBTN").classList.add("active");
			}
			document.getElementById("email").innerHTML = snaps.data().Email;
			document.getElementById("studentNO").innerHTML =
				snaps.data().StudentNo;

			var dateObj = new window.Date(snaps.data().Date);
			var month = dateObj.getUTCMonth() + 1;
			if (month < 10) {
				month = "0" + month;
			} else {
				month = month;
			}
			var day = dateObj.getUTCDate();
			var year = dateObj.getUTCFullYear();

			document.getElementById("date").innerHTML =
				year + " / " + month + " / " + day;

			document.getElementById("statusAppli").innerHTML =
				snaps.data().Status_Application;
		});
}
displayStatus();

function appliedCourses() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("appliedCourses")
		.onSnapshot((docSnap) => {
			const listAll = document.getElementById("list2");
			var div = "";
			var html = "";
			var count = 0;
			docSnap.forEach((eleSnap) => {
				count++;
				div = `
               
                ${eleSnap.data().Course}
                
               
                
           
                `;
				html += div;
				listAll.innerHTML = html;
			});
		});
}
appliedCourses();
const logout = document.querySelector(".logout_profiletop");
logout.addEventListener("click", (e) => {
	auth.signOut().then(() => {
		console.log("user signed out");
		location.href = "studentLogin.html";
	});
});
function processReg() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	window.location.href = "registrationProcess.html?User=" + ID + "";
}
const logout2 = document.querySelector(".logout_profiletop2");
logout2.addEventListener("click", (e) => {
	auth.signOut().then(() => {
		console.log("user signed out");
		location.href = "studentLogin.html";
	});
});
