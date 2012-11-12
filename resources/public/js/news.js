function voteUp(item, btn) {
    btn.disabled = true;
    $.ajax({
        type: 'POST',
        url: '/vote',
        data: {item:item},
        success: function(res, status, xhr) {
            var row = document.getElementById(item);
            var cell =row.getElementsByClassName("votes")[0];
            cell.innerHTML = res['votes'];
        },
        error: function(xhr, status, err){
            $('#messages').html("Cannot vote twice");
            console.log(err);
        }
    });
};

function deleteItem(id){
    $.ajax({
        type: 'delete',
        url: '/delete/' + id,
        success: function(res, status, xhr){
            console.log(res);
            // remove row
            var row = document.getElementById(id);
            row.parentNode.removeChild(row);
        },
        error: function(xhr, status, err){
            $('#messages').html("Cannot remove item" + id);
            console.log(err);
        }})
}

$(document).ready(function(){
    var signinLink = document.getElementById('signin');
    if (signinLink) {
        signinLink.onclick = function() { navigator.id.request(); };
        // signinLink.style.cursor = 'pointer';
    }

    var signoutLink = document.getElementById('signout');
    if (signoutLink) {
        signoutLink.onclick = function() { navigator.id.logout(); };
        // signoutLink.style.cursor = 'pointer';
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

var formsunhidden = [];

function showCommentForm(id){
    console.log("show comment form");
    div = document.getElementById('form:' + id);
    console.log(div);
    $(div).show('0.2');
    formsunhidden.push(id);
    // toggle command
    iden = 'div#'+ id.replace(':', '\\:') + ' span#commands a';
    l = $(iden)[1];
    l.innerHTML="hide";
    l.onclick = function (){
        $(div).hide('0.2');
        l.innerHTML="show";
        l.onclick = function () {showCommentForm(id);}
    };
}
