(function($, Configuration) {
	//console.log("Dataset test previewer for " + Configuration.dataset_id);
	//console.log("Updating tab " + Configuration.div);
    
    var dataset_id = Configuration.dataset_id;

	// Before creating chart, check if file count exceeds maximum size
	var max_size = 100;
	var dataset_size_req = $.ajax({
		type: "GET",
		url: jsRoutes.api.Datasets.datasetFilesList(dataset_id, true).url,
		dataType: "json"
	});
	dataset_size_req.done(function(data){
		if (data > max_size) {
			$(Configuration.div).append('<h4>File Types</h4>File count exceeds '+max_size+' files; skipping chart generation.');
			return
		} else {
			// setting up ajax call to get file from the dataset
			var file_req = $.ajax({
				type: "GET",
				url: jsRoutes.api.Datasets.datasetAllFilesList(dataset_id).url,
				dataType: "json"
			});

			file_req.done(function(data){

				// create data object with counts compatible with d3
				var counts = {};
				for(i=0;i<data.length;i++) {
					if (data[i].contentType in counts) {
						var plusOne = counts[data[i].contentType] + 1;
						counts[data[i].contentType] = plusOne;
					} else {
						counts[data[i].contentType] = 1;
					}
				}
				var array = [];
				for(var x in counts){
					array.push({occurences: counts[x], mime: x});
				}

				if (array.length > 0) {
					// add css
					$("<link/>", {
						rel: "stylesheet",
						type: "text/css",
						href: Configuration.path + "main.css"
					}).appendTo(Configuration.div);

					$(Configuration.div).append('<h4>File Types</h4>');

					$.getScript(Configuration.path + "/../../../d3js/d3.v3.min.js", function () {
						$.getScript(Configuration.path + "/../../../d3js/d3.legend.js", function () {
							var width = 500,
								height = 250,
								radius = Math.min(width, height) / 2;

							var color = d3.scale.category20();
							var arc = d3.svg.arc()
								.outerRadius(radius - 50)
								.innerRadius(radius - 80);

							var pie = d3.layout.pie()
								.sort(null)
								.value(function (d) {
									return d.occurences;
								});
							// container
							var svg = d3.select(Configuration.div).append("svg")
								.attr("width", width)
								.attr("height", height)
								.append("g")
								.attr("transform", "translate(" + (width / 2 - 150) + "," + height / 2 + ")");

							var g = svg.selectAll(".arc")
								.data(pie(array))
								.enter().append("g")
								.attr("class", "arc");

							g.append("path")
								.attr("d", arc)
								.attr("data-legend", function(d) { return d.data.mime; })
								.style("fill", function(d) { return color(d.data.mime); });

							var legend = svg.append("g")
								.attr("class", "legend")
								.attr("transform", "translate("+ (radius - 25) +","+(-radius + 35)+")")
								//.attr("style", "outline: thin solid lightGrey")
								.style("font-size","12px")
								.call(d3.legend);
						});
					});
				}
			});
		}
	});


}(jQuery, Configuration));