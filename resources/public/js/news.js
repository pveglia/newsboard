// function voteUp(item){
//     xml = new XMLHttpRequest();
//     xml.onreadystatechange=function(){
//         if(xml.readyState == 4 && xml.status == 200){
//             console.log(xml.responseText);
//             document.getElementById(item).innerHTML = xml.responseText;
//         }
//     }
//     xml.open("POST", "/vote");
//     xml.setRequestHeader("Content-type","application/x-www-form-urlencoded");
//     xml.send("item=" + item);
// }

function voteUp(item) {
    $.ajax({
        type: 'POST',
        url: '/vote',
        data: {item:item},
        success: function(res, status, xhr) {
            console.log(res['votes']);
            document.getElementById(item).innerHTML = res['votes'];
        },
        error: function(xhr, status, err){ alert("vote failure" + err); }
    });
};

$(document).ready(function(){
    var signinLink = document.getElementById('signin');
    if (signinLink) {
        signinLink.onclick = function() { navigator.id.request(); };
    }

    var signoutLink = document.getElementById('signout');
    if (signoutLink) {
        signoutLink.onclick = function() { navigator.id.logout(); };
    }

    navigator.id.watch({
        loggedInUser: currentUser,
        onlogin: function(assertion) {
            // A user has logged in! Here you need to:
            // 1. Send the assertion to your backend for verification and to create a session.
            // 2. Update your UI.
            $.ajax({ /* <-- This example uses jQuery, but you can use whatever you'd like */
                type: 'POST',
                url: '/auth/login', // This is a URL on your website.
                data: {assertion: assertion},
                success: function(res, status, xhr) { window.location.reload(); },
                error: function(xhr, status, err) { alert("login failure" + err); }
            });
        },
        onlogout: function() {
            // A user has logged out! Here you need to:
            // Tear down the user's session by redirecting the user or making a call to your backend.
            // Also, make sure loggedInUser will get set to null on the next page load.
            // (That's a literal JavaScript null. Not false, 0, or undefined. null.)
            $.ajax({
                type: 'POST',
                url: '/auth/logout', // This is a URL on your website.
                success: function(res, status, xhr) { window.location.reload(); },
                error: function(xhr, status, err) { alert("logout failure" + err); }
            });
        }
    });
})
