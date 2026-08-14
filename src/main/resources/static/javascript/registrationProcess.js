function processReg2() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	var entry = document.getElementsByClassName("checks");
	var str = "";
	for (i = 0; i < 1; i++) {
		if (entry[i].checked === true) {
			str += entry[i].value;

			window.location.href = "registrationProcess2.html?User=" + ID + "";
		} else {
			swal("Please accept Terms and Conditions!");
		}
	}
}
