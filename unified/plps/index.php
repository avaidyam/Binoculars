<!doctype html>
<html lang="en">
    <head>
        <meta charset="utf-8" />
        <meta http-equiv="X-UA-Compatible" content="IE=edge" />
        <meta name="description" content="PL-PatchSurfer Frontend" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black" />
        <meta name="apple-mobile-web-app-title" content="PL-PatchSurfer" />
        <title>PL-PatchSurfer</title>
        <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:regular,bold,italic,thin,light,bolditalic,black,medium&amp;lang=en" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
        <link rel="stylesheet" href="https://code.getmdl.io/1.3.0/material.indigo-pink.min.css">
        <script defer src="https://code.getmdl.io/1.3.0/material.min.js"></script>
        <style>html>body{font-family:'Roboto','Helvetica','Arial',sans-serif!important;}</style>
    </head>
    <body>
        <div class="mdl-layout mdl-js-layout mdl-layout--fixed-header">
            <header class="mdl-layout__header">
                <div class="mdl-layout__header-row">
                    <span class="mdl-layout-title">PL-PatchSurfer</span>
                    <div class="mdl-layout-spacer"></div>
                </div>
                <div class="mdl-layout__tab-bar mdl-js-ripple-effect">
                    <a href="#action-tab" class="mdl-layout__tab is-active">Run</a>
                    <a href="#help-tab" class="mdl-layout__tab">Help</a>
                </div>
            </header>
            <div class="mdl-layout__drawer">
                <span class="mdl-layout-title">Kihara Lab</span>
            </div>
            <main class="mdl-layout__content">
                <section class="mdl-layout__tab-panel is-active" id="action-tab">
                    <div class="mdl-grid">
                        <div class="mdl-cell mdl-cell--2-col mdl-cell--hide-tablet mdl-cell--hide-phone"></div>
                        <div class="mdl-card mdl-shadow--16dp content mdl-cell mdl-cell--8-col" style="margin-top:32px;padding:32px;">
                            <iframe name="_result" width="0" height="0" border="0" style="display: none;"></iframe>
                            <form id="form" method="POST" enctype="multipart/form-data" action="../job_bridge.php" onsubmit="return validateForm()" target="_result">
                                <div style="height: 22px;">
                                    <label for="receptor">Receptor:</label>
                                    <input type="receptor" id="receptor" name="receptor" required />
                                </div>
                                <div class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label" style="width:18%">
                                    <input class="mdl-textfield__input" type="text" pattern="^.{0,5}$" id="chain" required />
                                    <label class="mdl-textfield__label" for="chain">Chain ID...</label>
                                    <span class="mdl-textfield__error">Must be a chain ID</span>
                                </div>
                                <div class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label" style="width:18%">
                                    <input class="mdl-textfield__input" type="text" pattern="^.{0,5}$" id="ligand" required />
                                    <label class="mdl-textfield__label" for="ligand">Ligand ID...</label>
                                    <span class="mdl-textfield__error">Must be a ligand ID</span>
                                </div>
                                <div style="width:100%;"></div> <!--\n-->
                                <div class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
                                    <select class="mdl-textfield__input" id="db" name="db" required>
                                        <option value="chembl_19">chembl_19</option>
                                        <option value="zinc_druglike">zinc_druglike</option>
                                        <option value="debug_1">debug_1</option>
                                        <option value="debug_2">debug_2</option>
                                    </select>
                                    <label class="mdl-textfield__label" for="db">Database</label>
                                </div>
                                <div style="width:100%;"></div> <!--\n-->
                                <div class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label">
                                    <input class="mdl-textfield__input" type="text" pattern="^[^\s@]+@[^\s@]+\.[^\s@]+$" id="email" required />
                                    <label class="mdl-textfield__label" for="email">Email...</label>
                                    <span class="mdl-textfield__error">Input is not an email!</span>
                                </div>
                                <div style="margin-top:8px;height: 28px;">
                                    <input type="hidden" name="service" value="plps" />
                                    <input id="form-submit" class="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
                                        type="submit" value="Submit Job" />
                                </div>
                            </form>
                        </div>
                    </div>
                </section>
                <section class="mdl-layout__tab-panel" id="help-tab">
                    <div class="mdl-grid">
                        <div class="mdl-cell mdl-cell--2-col mdl-cell--hide-tablet mdl-cell--hide-phone"></div>
                        <div class="mdl-card mdl-shadow--16dp content mdl-cell mdl-cell--8-col" style="margin-top:32px;padding:32px;">
                            <p>
                            PL-PatchSurfer2 is a virtual screening program that matches local surface
                            patches of receptor pocket and ligand that are represented by three-dimensional
                            Zernike decriptors. Four physico-chemical features are used to describe surface
                            patch: shape, electrostatic potential, hydrophobicity, and hydrogen bonding.
                            </p>
                            <hr />
                            <p>
                            To use the tool, submit a PDB file containing a protein with at least one subunit
                            and one ligand (HETATM), along with the IDs of both. Your job will fail if the IDs
                            given don't exist in the PDB file uploaded. In addition, select the database you wish
                            to submit your job against, and provide a valid email to receive job progress at.
                            </p>
                            <hr />
                            <p>
                            <b>References</b>
                            <ol>
                                <li>Shin, W. -H.; Christoffer, C. W.; Wang, J.; Kihara, D.<br />
                                    PL-PatchSurfer2: Improved Local Surface Matching-Based Virtual Screening<br />
                                    Method that is Tolerant to Target and Ligand Structure Variation
                                </li>
                                <li>
                                    J. Chem. Inf. Model. Submitted.
                                </li>
                                <li>
                                    Shin, W. -H.; Bures, M. G.; Kihara, D.<br />
                                    PatchSurfers: Two methods for local molecular property-based binding<br />
                                    ligand prediction
                                    Methods 2016, 93, 41-50.
                                </li>
                                <li>Hu, B.; Zhu, X.; Monroe, L.; Bures, M. G.; Kihara, D.<br />
                                    PL-PatchSurfer: A novel molecular local surface-based method for exploring<br />
                                    protein-ligand interactions.<br />
                                    Int. J. Mol. Sci. 2014, 15, 15122-15145.
                                </li>
                            </ol>
                            </p>
                        </div>
                    </div>
                </section>
            </main>
            <footer class="mdl-mini-footer">
                <div class="mdl-mini-footer--left-section">
                    <ul class="mdl-mini-footer--link-list">
                        <li>Copyright © 2016 <a href="http://kiharalab.org/">Kihara Lab</a></li>
                    </ul>
                </div>
            </footer>
            <script>
                function validateForm() {
                    if (!document.forms["form"]["receptor"].checkValidity()) {
                        alert("No PDB file selected.");
                        return false;
                    }
                    if (!document.forms["form"]["chain"].checkValidity()) {
                        alert("No chain ID provided.");
                        return false;
                    }
                    if (!document.forms["form"]["ligand"].checkValidity()) {
                        alert("No ligand ID provided.");
                        return false;
                    }
                    if (!document.forms["form"]["db"].checkValidity()) {
                        alert("No ligand database selected.");
                        return false;
                    }
                    if (!document.forms["form"]["email"].checkValidity()) {
                        alert("No recipient email provided.");
                        return false;
                    }
                    alert("Job submitted!");
                    document.forms["form"].reset();
                    return true;
                }
            </script>
        </div>
    </body>
</html>
