
document.addEventListener("DOMContentLoaded", function(event) {
  var teamsButton = document.getElementById("leaderboard-picker-teams");
  var regionsButton = document.getElementById("leaderboard-picker-regions");

  var teamsTable = document.getElementById("team-leaderboard")
  var regionsTable = document.getElementById("regional-leaderboard")

  regionsTable.style.display = "none";

  regionsButton.onclick = function() {
    if (!regionsButton.classList.contains("active")) {
      teamsButton.classList.remove("active");
      regionsButton.classList.add("active");

      regionsTable.style.display = "";
      teamsTable.style.display = "none";
    }
    regionsButton.blur();
  };

  teamsButton.onclick = function() {
    if (!teamsButton.classList.contains("active")) {
      teamsButton.classList.add("active");
      regionsButton.classList.remove("active");

      regionsTable.style.display = "none";
      teamsTable.style.display = "";
    }
    teamsButton.blur();
  };
});
