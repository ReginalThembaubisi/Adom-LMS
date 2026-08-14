function appliedCourses() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("appliedCourses")
		.onSnapshot((docSnap) => {
			const listAll = document.getElementById("list");
			var div = "";
			var html = "";
			var count = 0;
			docSnap.forEach((eleSnap) => {
				count++;
				div = `
                <tr>
                <th scope="row">${count}</th>
                <td>${eleSnap.data().Course_Code}</td>
                <td>${eleSnap.data().Course}</td>
                <td>
                <span class="edit" onclick="remove('${eleSnap.id}')"
											><i class="fas fa-minus-circle"></i>
											Remove</span</td>
               
                
            </tr>
                `;
				html += div;
				listAll.innerHTML = html;
			});
		});
}

appliedCourses();
function process7() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess8.html?User=" + ID + "";
}
function back() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");
	window.location.href = "applicationProcess6.html?User=" + ID + "";
}
