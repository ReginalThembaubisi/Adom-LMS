function getListofregis() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("registeredCourses")
		.doc("96325")
		.collection("Modules")
		.onSnapshot((data) => {
			var html = "";
			var div = "";
			const list = document.getElementById("listMod");
			var Totalprice = 0;
			data.forEach((element) => {
				Totalprice += element.data().Regi_fees;

				document.getElementById("totalPriceRegistration").innerHTML =
					"R " + Totalprice;
			});
		});
}
getListofregis();
