<?php
    if ($_FILES["file"]["error"] > 0) {
        echo "Return Code: " . $_FILES["file"]["error"] . "<br>";
    } else {
        $service = $_POST["service"] ?: 'none';
        $manifest = "";
        foreach ($_POST as $label => $val) {
            $manifest .= $label . ': ' . $val . "\n";
        }
        
        $upload_folder = "upload/" . $service . '_' . uniqid() . '/';
        $upload_folder = getcwd() . "/" . $upload_folder;
        foreach ($_FILES as $label => $file) {
            echo "Upload: " . $file["name"] . "<br />";
            echo "Type: " . $file["type"] . "<br />";
            echo "Size: " . ($file["size"] / (1024 * 1024)) . " MB<br />";
            echo "Temp file: " . $file["tmp_name"] . "<br />";
            $manifest .= $label . ': ' . $file["name"] . "\n";

            $upload_file = $upload_folder . $file["name"];
            if (file_exists($upload_file)) {
                echo $file["name"] . " already exists. ";
            } else {
                echo $upload_folder.$title . " " . $upload_folder . "<br />";
                if (mkdir($upload_folder.$title, (int) 0777)) {
                    chmod($upload_folder.$title, 0777);
                    echo "MKDIR Success!<br />";
                } else {
                    echo "MKDIR Failure!<br />";
                }
                
                if (move_uploaded_file($file["tmp_name"], $upload_file)) {
                    chmod($upload_file, 0777);
                    echo "Stored in: " .$upload_file;
                } else {
                    echo "Failed to upload file";
                }
            }
            echo "<br /><br />";
        }
        
        file_put_contents($upload_folder . 'manifest.mf', $manifest);
        rmdir($upload_folder);
    }
?>
