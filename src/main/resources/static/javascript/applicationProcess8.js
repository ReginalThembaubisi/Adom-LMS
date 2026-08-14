function uploadID() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
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
	task.on("state_changed", function progress(snapshot) {
		var percentage =
			(snapshot.bytesTransferred / snapshot.totalBytes) * 100;
		document.getElementById("uploader").value = percentage;
		document.getElementById("UpProgress").innerHTML =
			"Upload " + percentage.toFixed(0) + "%";
	});

	task.then((snapshot) => snapshot.ref.getDownloadURL())
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
					swal("Successful", "ID added", "success");
				});
		})
		.catch(console.error);
}
function display() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("ID")
		.doc("96325")
		.onSnapshot((idd) => {
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${
					idd.data().File_name
				} </a> &nbsp;&nbsp;&nbsp;<span class="edit" onclick="remove('${
					idd.data().Url
				}')"
										><i class="fas fa-minus-circle"></i>
										Remove</span
				`;
				document.getElementById("uploader").style.display = "none";
				document.getElementById("UpProgress").style.display = "none";
			} else {
			}
		});
}
display();
function remove(urlFile) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	var fileUrl = urlFile;

	// Create a reference to the file to delete
	var fileRef = firebase.storage().refFromURL(fileUrl);

	// Delete the file using the delete() method
	fileRef
		.delete()
		.then(function () {
			// File deleted successfully
			console.log("File Deleted");
			db.collection("studentApplication")
				.doc(ID)
				.collection("uploadedDocuments")
				.doc("96325")
				.collection("ID")
				.doc("96325")
				.delete();

			display();
			swal("Successful", "ID Document deleted", "success");

			setTimeout(() => {
				window.location.reload();
			}, 2000);
		})
		.catch(function (error) {
			// Some Error occurred
		});
}

function display1() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("secondaryResults")
		.doc("96325")
		.onSnapshot((idd) => {
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID2").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${
					idd.data().File_name
				} </a> &nbsp;&nbsp;&nbsp;<span class="edit" onclick="remove1('${
					idd.data().Url
				}')"
										><i class="fas fa-minus-circle"></i>
										Remove</span
				`;
				document.getElementById("uploader2").style.display = "none";
				document.getElementById("UpProgress2").style.display = "none";
			} else {
			}
		});
}
display1();
function remove1(urlFile) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	var fileUrl = urlFile;

	// Create a reference to the file to delete
	var fileRef = firebase.storage().refFromURL(fileUrl);

	// Delete the file using the delete() method
	fileRef
		.delete()
		.then(function () {
			// File deleted successfully
			console.log("File Deleted");
			db.collection("studentApplication")
				.doc(ID)
				.collection("uploadedDocuments")
				.doc("96325")
				.collection("secondaryResults")
				.doc("96325")
				.delete();

			display();
			swal("Successful", "Secondary document deleted", "success");

			setTimeout(() => {
				window.location.reload();
			}, 2000);
		})
		.catch(function (error) {
			// Some Error occurred
		});
}
function display2() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("uploadedDocuments")
		.doc("96325")
		.collection("tertiaryTranscript")
		.doc("96325")
		.onSnapshot((idd) => {
			if (idd.data().Url != "" || idd.data().Url) {
				document.getElementById("preloadID3").innerHTML = `
				<a href="${idd.data().Url}" target= "_blank">${
					idd.data().File_name
				} </a> &nbsp;&nbsp;&nbsp;<span class="edit" onclick="remove2('${
					idd.data().Url
				}')"
										><i class="fas fa-minus-circle"></i>
										Remove</span
				`;
				document.getElementById("uploader3").style.display = "none";
				document.getElementById("UpProgress3").style.display = "none";
			} else {
			}
		});
}
display2();
function remove2(urlFile) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	var fileUrl = urlFile;

	// Create a reference to the file to delete
	var fileRef = firebase.storage().refFromURL(fileUrl);

	// Delete the file using the delete() method
	fileRef
		.delete()
		.then(function () {
			// File deleted successfully
			console.log("File Deleted");
			db.collection("studentApplication")
				.doc(ID)
				.collection("uploadedDocuments")
				.doc("96325")
				.collection("tertiaryTranscript")
				.doc("96325")
				.delete();

			display();
			swal("Successful", "Tertiary Transcript deleted", "success");

			setTimeout(() => {
				window.location.reload();
			}, 2000);
		})
		.catch(function (error) {
			// Some Error occurred
		});
}
function uploadResults() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	const ref = firebase.storage().ref("Recent Results/");
	const file = document.querySelector("#Recent-results").files[0];

	if (file == "") {
		swal("Please Select Results before you can Upload!");
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
	task.on("state_changed", function progress(snapshot) {
		var percentage =
			(snapshot.bytesTransferred / snapshot.totalBytes) * 100;
		document.getElementById("uploader2").value = percentage;
		document.getElementById("UpProgress2").innerHTML =
			"Upload " + percentage.toFixed(0) + "%";
	});

	task.then((snapshot) => snapshot.ref.getDownloadURL())
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
					swal("Successful", "Results added", "success");
				});
		})
		.catch(console.error);
}
function tertiary() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("tertiaryDetails")
		.doc("96325")
		.get()
		.then((snap) => {
			var status = snap.data().Include_Tertiary_Results;
			console.log(status);
			if (status == "no") {
				document.getElementById("tert").classList.remove("active");
			} else {
				document.getElementById("tert").classList.add("active");
				var btnTertiaryUpload = document.getElementById("tertiaryBTN");
				btnTertiaryUpload.addEventListener("click", () => {
					const ref = firebase.storage().ref("Tertiary Transcript/");
					const file = document.querySelector("#transcript-results")
						.files[0];

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
							(snapshot.bytesTransferred / snapshot.totalBytes) *
							100;
						document.getElementById("uploader3").value = percentage;
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
									swal(
										"Successful",
										"Tertiary Transcript added",
										"success"
									);
								});
						})
						.catch(console.error);
				});
			}
		});
}
tertiary();
function process8() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess9.html?User=" + ID + "";
}
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess7.html?User=" + ID + "";
}
