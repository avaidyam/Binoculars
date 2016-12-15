<?php
	if ($_FILES["file"]["error"] > 0) {
		echo "Return Code: " . $_FILES["file"]["error"] . "<br>";
	} else {

		$upload_folder = "upload/";
		$upload_folder = getcwd() . "/" . $upload_folder;
		foreach ($_FILES as $file) {
			echo "Upload: " . $file["name"] . "<br>";
			echo "Type: " . $file["type"] . "<br>";
			echo "Size: " . ($file["size"] / 1024) . " kB<br>";
			echo "Temp file: " . $file["tmp_name"] . "<br>";

			if (file_exists($upload_folder . $file["name"])) {
				echo $file["name"] . " already exists. ";
			} else {
				$try_mkdir = mkdir($upload_folder.$title, 0777);
				if ($try_mkdir) {
					echo "MKDIR Success!<br>";
				}
				else {
					echo "MKDIR Failure!<br>";
				}
				move_uploaded_file($file["tmp_name"],
				$upload_folder.$title . $file["name"]);
				echo "Stored in: " .$upload_folder.$title . $file["name"];
			}
			echo "<br>";
		}
	}
?>
