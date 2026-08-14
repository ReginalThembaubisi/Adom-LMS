function process5() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	var name = document.getElementById("name").value;
	var email = document.getElementById("email").value;
	var relationship = document.getElementById("relationship").value;
	var cellphone = document.getElementById("cellphone").value;

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

		var postal4 = document.getElementById("postal2").value;

		var postalAddress = { postal1, postal2, postal3, postal4 };
		db.collection("studentApplication")
			.doc(ID)
			.collection("kinDetails")
			.doc("96325")
			.set({
				Name: name,
				Relationship: relationship,
				Email: email,
				Cellphone: cellphone,
				Status: status,
				Physical_Address: physicalAddress,
				Postal_Address: postalAddress,
			})
			.then(() => {
				window.location.href =
					"applicationProcess5.html?User=" + ID + "";
			});
	} else {
		db.collection("studentApplication")
			.doc(ID)
			.collection("kinDetails")
			.doc("96325")
			.set({
				Name: name,
				Relationship: relationship,
				Email: email,
				Cellphone: cellphone,
				Status: status,
				Physical_Address: physicalAddress,
			})
			.then(() => {
				window.location.href =
					"applicationProcess5.html?User=" + ID + "";
			});
	}
}
function display() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("kinDetails")
		.doc("96325")
		.get()
		.then((snaps) => {
			document.getElementById("name").value = snaps.data().Name;
			document.getElementById("email").value = snaps.data().Email;
			document.getElementById("relationship").value =
				snaps.data().Relationship;
			document.getElementById("cellphone").value = snaps.data().Cellphone;

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
	window.location.href = "applicationProcess3.html?User=" + ID + "";
}
