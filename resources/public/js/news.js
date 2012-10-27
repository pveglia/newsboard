function voteUp(item){
    xml = new XMLHttpRequest();
    xml.onreadystatechange=function(){
        if(xml.readyState == 4 && xml.status == 200){
            console.log(xml.responseText);
            document.getElementById("notification").innerHTML = "Successfully voted";
            document.getElementById(item).innerHTML = xml.responseText;
        }
    }
    xml.open("POST", "/vote");
    xml.setRequestHeader("Content-type","application/x-www-form-urlencoded");
    xml.send("item=" + item);
}
