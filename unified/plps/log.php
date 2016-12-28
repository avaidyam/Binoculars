<?php
$logFile = "/net/kihara/avaidyam/daemon.log";
$interval = 3000;

if($_GET['echo']){
	echo file_get_contents($logFile);
} else {?>
<html>
	<title><?php echo basename($logFile); ?></title>
	<style>
		@import url(http://fonts.googleapis.com/css?family=Roboto+Mono);
		body {
			background-color: black;
			color: #ffffff;
			font-family: 'Roboto Mono', monospace;
			font-size: 11px;
			line-height: 14px;	
		}
		h4 {
			font-size: 18px;
			line-height: 22px;
			color: #cccccc;
		}
		#log {
			position: relative;
			top: -34px;
		}
		#scrollLock {
			width:2px;
			height: 2px;
			overflow:visible;
		}
	</style>
	<script src="//ajax.googleapis.com/ajax/libs/jquery/1.8.0/jquery.min.js" type="text/javascript"></script>
	<script>
		setInterval(readLogFile, <?php echo $interval; ?>);
		window.onload = readLogFile; 
		var pathname = window.location.pathname;
		var scrollLock = true;
		$(document).ready(function(){
			$('.disableScrollLock').click(function(){
				$("html,body").clearQueue()
				$(".disableScrollLock").hide();
				$(".enableScrollLock").show();
				scrollLock = false;
			});
			$('.enableScrollLock').click(function(){
				$("html,body").clearQueue()
				$(".enableScrollLock").hide();
				$(".disableScrollLock").show();
				scrollLock = true;
			});
		});
		function readLogFile(){
			$.get(pathname, { echo : "true" }, function(data) {
				data = data.replace(new RegExp("\n", "g"), "<br />");
		        $("#log").html(data);
		        if(scrollLock == true) { 
				$('html,body').animate({scrollTop: $("#scrollLock").offset().top}, <?php echo $interval; ?>) 
			};
		    });
		}
	</script>
	<body>
		<h4><?php echo $logFile; ?></h4><br />
		<div id="log">
			
		</div>
		<div id="scrollLock">
			<input class="disableScrollLock" type="button" value="Disable Scroll Lock" />
			<input class="enableScrollLock" style="display: none;" type="button" value="Enable Scroll Lock" />
		</div>
	</body>
</html>
<?php  } ?>
