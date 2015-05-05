<?php
$host = 'http://' . $_SERVER['SERVER_NAME'] . ':9000/';
$key = 'r1ek3rs';

if(isset($_REQUEST['form'])){		//Present form
	echo '<form method="get">' . "<br>\n";
	echo 'File URL: <input type="text" name="url" size="100">' . "<br>\n";
	echo '<input type="submit" value="Extract">' . "<br>\n";
	echo '</form>' . "<br>\n";
}

if(isset($_REQUEST['url'])){		//Extract information from file at given URL
	$url = $_REQUEST['url'];

	//Upload file
	$options = array(
		'http' => array(
			'method' => 'POST',
			'header' => 'Content-type: application/json',
			'content' => json_encode(array('fileurl' => $url))
		)
	);

	$file_id = json_decode(file_get_contents($host . 'api/extractions/upload_url?key=' . $key, false, stream_context_create($options)), true);
	$file_id = $file_id['id'];

	//Poll until output is ready (optional)
	while(true){
		$status = json_decode(file_get_contents($host . 'api/extractions/' . $file_id . '/status'), true);
		if($status['Status'] == 'Done') break;
		sleep(1);
	}
		
	//Display extracted content
	$options = array(
		'http' => array(
			'method' => 'GET',
			'header' => 'Content-type: application/json'
		)
	);

	$metadata = json_decode(file_get_contents($host . 'api/extractions/' . $file_id . '/metadata', false, stream_context_create($options)), true);
	$metadata['technicalmetdata'] = json_decode(file_get_contents($host . 'api/files/' . $file_id . '/technicalmetadatajson', false, stream_context_create($options)), true);
	$metadata = json_encode($metadata);
	echo $metadata;
}
?>
