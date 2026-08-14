function register() {
	var Uname = document.getElementById("username").value;
	var Pword = document.getElementById("password").value;
	var Cpword = document.getElementById("confirmpassword").value;
	var Cellno = document.getElementById("cellnumber").value;
	var Email = document.getElementById("email").value;

	console.log(Uname, Pword, Cpword, Cellno, Email);

	window.location.href = "home.html";
}

function login() {
	var Uname = document.getElementById("username").value;
	var Pword = document.getElementById("password").value;

	console.log(Uname, Pword);

	window.location.href = "home.html";
}
