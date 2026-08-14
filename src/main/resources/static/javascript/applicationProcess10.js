function getOutCome() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	db.collection("studentApplication")
		.doc(ID)
		.collection("statusOfapplication")
		.doc("96325")
		.get()
		.then((snaps) => {
			const display = document.getElementById("dispay");

			var html = `
                                        <h4>
									Thank you for Submitting your Application
								</h4>
								<h6>
									We will let you know of the outcome soon
								</h6>
                                <br />  <br /> 
								<h5>Here is your details</h5>
                                <br />
								<span class="studentNo"
									>Student no: ${snaps.data().StudentNo}</span
								><br />
                                <h6>
                                Log in with the following to view your
                                status
                            </h6>
                            <br />
                            <span class="pin"
                                >Username: ${snaps.data().Email}</span
                            > <br />
                            <span class="pin"
                                >Password: ${snaps.data().Password}</span
                            >
                                        
                                        `;
			display.innerHTML = html;
		});
}
getOutCome();
function process11() {
	window.location.href = "applicationProcess.html";
}
