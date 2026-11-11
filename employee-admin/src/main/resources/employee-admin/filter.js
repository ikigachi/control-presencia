const filter = document.querySelector("#employee-filter");
const rows = [...document.querySelectorAll("#employee-rows tr")];
const noMatches = document.querySelector("#no-matches");

if (rows.length) {
    document.querySelector("#filter-control").hidden = false;
    filter.addEventListener("input", () => {
        const query = filter.value.trim().toLowerCase();
        let matches = 0;
        for (const row of rows) {
            row.hidden = !row.cells[0].textContent.toLowerCase().includes(query);
            if (!row.hidden) matches++;
        }
        noMatches.hidden = matches !== 0;
    });
}
