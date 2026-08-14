var y = "yes";
var n = "no";
var chosed = false;
function option() {
	setTimeout(() => {
		var answer1 = document.getElementById("addressSame").value;
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
	document.getElementById("postal").classList.remove("active");
	option();
}
function no() {
	document.getElementById("postal").classList.add("active");
	option();
	chosed = true;
}
function process4() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	var email = document.getElementById("email").value;

	var confrimEmail = document.getElementById("confirmEmail").value;

	var cellphone = document.getElementById("cellphone").value;

	var confirmCellphone = document.getElementById("confirmCellphone").value;

	var address1 = document.getElementById("address1").value;

	var address2 = document.getElementById("address2").value;

	var address3 = document.getElementById("address3").value;

	var address4 = document.getElementById("address4").value;

	var physicalAddress = { address1, address2, address3, address4 };
	var status = document.getElementById("addressSame").value;

	if (status == "no") {
		var postal1 = document.getElementById("postal1").value;

		var postal2 = document.getElementById("postal2").value;

		var postal3 = document.getElementById("postal3").value;

		var postal4 = document.getElementById("postal4").value;

		var postalAddress = { postal1, postal2, postal3, postal4 };
		db.collection("studentApplication")
			.doc(ID)
			.collection("contactDetails")
			.doc("96325")
			.set({
				Email: email,
				Cellphone: cellphone,
				Status: status,
				Physical_Address: physicalAddress,
				Postal_Address: postalAddress,
			})
			.then(() => {
				window.location.href =
					"applicationProcess4.html?User=" + ID + "";
			});
	} else {
		db.collection("studentApplication")
			.doc(ID)
			.collection("contactDetails")
			.doc("96325")
			.set({
				Email: email,
				Cellphone: cellphone,
				Status: status,
				Physical_Address: physicalAddress,
			})
			.then(() => {
				window.location.href =
					"applicationProcess4.html?User=" + ID + "";
			});
	}
}

function display() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("contactDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("email").value = snaps.data().Email;

			document.getElementById("confirmEmail").value = snaps.data().Email;

			document.getElementById("cellphone").value = snaps.data().Cellphone;

			document.getElementById("confirmCellphone").value =
				snaps.data().Cellphone;

			document.getElementById("address1").value =
				snaps.data().Physical_Address.address1;

			document.getElementById("address2").value =
				snaps.data().Physical_Address.address2;

			document.getElementById("address3").value =
				snaps.data().Physical_Address.address3;

			document.getElementById("address4").value =
				snaps.data().Physical_Address.address4;
			document.getElementById("addressSame").value = snaps.data().Status;
			document.getElementById("postal1").value =
				snaps.data().Postal_Address.postal1;
			document.getElementById("postal2").value =
				snaps.data().Postal_Address.postal2;

			document.getElementById("postal3").value =
				snaps.data().Postal_Address.postal3;

			document.getElementById("postal4").value =
				snaps.data().Postal_Address.postal4;
		});
}
display();
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess2.html?User=" + ID + "";
}
