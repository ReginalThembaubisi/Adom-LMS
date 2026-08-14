function listOfcourseModules() {
	var html = "";
	var div = "";
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("Admin")
		.doc("Admission")
		.collection("Courses")
		.doc("4GGy7QnzWMKi8lqa5zrz")
		.collection("Modules")
		.get()
		.then((courseApplied) => {
			const list = document.getElementById("regiList");
			var count = 0;
			courseApplied.forEach((modu) => {
				count++;
				console.log(modu.data().Module_Name);

				div = `
                <tr onclick="get('${modu.id}')">
                <td>${modu.data().Module_Name}</td>
                <td>${modu.data().NQFLevel}</td>
                <td>${modu.data().Credits}</td>
                <td>${modu.data().Duration_Months}</td>
                <td> R ${modu.data().Regi_fees}</td>
                <td>R ${modu.data().per_month}</td>
                <td>R ${modu.data().Total}</td>
               
            </tr>
                
                `;
				html += div;
				list.innerHTML = html;
			});
		});
}
listOfcourseModules();

function get(m) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("Admin")
		.doc("Admission")
		.collection("Courses")
		.doc("4GGy7QnzWMKi8lqa5zrz")
		.collection("Modules")
		.doc(m)
		.get()
		.then((mod) => {
			console.log(mod.data().Module_Name);

			db.collection("studentApplication")
				.doc(ID)
				.collection("registeredCourses")
				.doc("96325")
				.collection("Modules")
				.add({
					Module_Name: mod.data().Module_Name,
					NQFLevel: mod.data().NQFLevel,
					Credits: mod.data().Credits,
					Duration_Months: mod.data().Duration_Months,
					Regi_fees: mod.data().Regi_fees,
					per_month: mod.data().per_month,
					Total: mod.data().Total,
					ShortModuleName: mod.data().MoShortName,
					LectureModule: mod.data().Lecture,
				})
				.then(() => {
					swal("Successful", "Course Added successfully", "success", {
						buttons: false,
						timer: 2000,
					});
				});
		});
}

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
				div = `
                <tr>
                <td>${element.data().Module_Name}</td>
									
									<td>
										<span class="edit" onclick="remove('${element.id}')"
											><i class="fas fa-minus-circle"></i>
											Remove</span
										>
									</td>
                                    </tr>
                `;
				Totalprice += element.data().Regi_fees;
				html += div;
				list.innerHTML = html;
				document.getElementById("totalPriceRegistration").innerHTML =
					"R " + Totalprice;
			});
		});
}
getListofregis();
function remove(subjectkey) {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("registeredCourses")
		.doc("96325")
		.collection("Modules")
		.doc(subjectkey)
		.delete();
	getListofregis();
	swal("Successful", "module Deleted (If not reload page)", "success");
}

function processReg4() {
	const query = window.location.search;
	const url = new URLSearchParams(query);
	const ID = url.get("User");

	db.collection("studentApplication")
		.doc(ID)
		.collection("statusOfapplication")
		.doc("96325")
		.update({
			Status_Application: "Temporaly_Registered",
		})
		.then(() => {
			window.location.href = "registrationProcess4.html?User=" + ID + "";
		});
}
