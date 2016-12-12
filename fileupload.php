<?php
	if ($_FILES["file"]["error"] > 0) {
		echo "Return Code: " . $_FILES["file"]["error"] . "<br>";
	} else {

		$upload_folder = "upload/";
		echo "Upload: " . $_FILES["file"]["name"] . "<br>";
		echo "Type: " . $_FILES["file"]["type"] . "<br>";
		echo "Size: " . ($_FILES["file"]["size"] / 1024) . " kB<br>";
		echo "Temp file: " . $_FILES["file"]["tmp_name"] . "<br>";

		if (file_exists($upload_folder . $_FILES["file"]["name"])) {
			echo $_FILES["file"]["name"] . " already exists. ";
		} else {
			mkdir($upload_folder.$title, 0700);
			move_uploaded_file($_FILES["file"]["tmp_name"],
			$upload_folder.$title . $_FILES["file"]["name"]);
			echo "Stored in: " .$upload_folder.$title . $_FILES["file"]["name"];
		}
	}
?>