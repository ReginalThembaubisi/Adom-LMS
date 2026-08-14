
function sendApplication()
{
    swal("Successful", "Your application has been sent", "success");

    setTimeout(()=>{
        
        window.location.href="index.html"
    },3000)

}
function job()
{
    window.location.href="list-jobs.html"
}

