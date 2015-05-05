function sectionRubberband(prNum) {
    window["mousedown" + prNum] = {};
    window["rubberbandRectangle" + prNum] = {};
    window["dragging" + prNum] = false;
    window["pageno" + prNum] = 1;
    window["page" + prNum] = {};
    window["pdf" + prNum] = {};

    $("#rubberbandCanvas" + prNum).css("cursor", "crosshair");

    // ----------------------------------------------------------------------
    // CANVAS MOUSE EVENT HANDLERS
    // ----------------------------------------------------------------------
    $("#rubberbandCanvas"+prNum).on("mousedown", function (e) {
        var x = e.offsetX;
        var y = e.offsetY;

        e.preventDefault();
        rubberbandStart(x, y,prNum);
    });

    $("#rubberbandCanvas"+prNum).on("mousemove", function (e) {
        var x = e.offsetX;
        var y = e.offsetY;

        e.preventDefault();
        if (window["dragging" + prNum]) {
            rubberbandStretch(x, y,prNum);
        }
    });

    $("#rubberbandCanvas"+prNum).on("mouseup", function (e) {
        e.preventDefault();
        rubberbandEnd(prNum);
    });

    // ----------------------------------------------------------------------
    // FORM SUBMISSIONS
    // ----------------------------------------------------------------------
    $("#rubberbandFormSubmit"+prNum).on("click", function(e) {
        // quick check
        var tag = $("#rubberbandFormTag"+prNum).val();
        var comment = $("#rubberbandFormComment"+prNum).val();
        if ((tag == "") && (comment == "")) {
            resetRubberband(prNum);
            return false;
        }

        // get selected rectangle
        var canvas = $("#rubberbandCanvas"+prNum)[0];
        var x = window["rubberbandRectangle" + prNum].left / canvas.width;
        var y = window["rubberbandRectangle" + prNum].top / canvas.height;
        var w = window["rubberbandRectangle" + prNum].width / canvas.width;
        if (x + w > 1) {
            w = 1.0 - x;
        }
        if (w <= 0) {
            resetRubberband(prNum);
            return false;
        }
        var h = window["rubberbandRectangle" + prNum].height / canvas.height;
        if (y + h > 1) {
            h = 1.0 - y;
        }
        if (h <= 0) {
            resetRubberband(prNum);
            return false;
        }

        // create section
        var sectionid = "";
        var request = window.jsRoutes.api.Sections.add().ajax({
            type: 		 "POST",
            contentType: "application/json",
            data:		 JSON.stringify({
                file_id: window["configsFileId" + prNum],
                area: {
                    x:	    x,
                    y: 	    y,
                    w:	    w,
                    h:	    h,
                },
            }),
        });
        request.done(function(response, textStatus, jqXHR) {
            sectionCreated(tag, comment, response.id, x, y, w, h, prNum);
        });
        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
        });

        resetRubberband(prNum);
        return false;
    });

    $("#rubberbandFormCancel"+prNum).on("click", function(e) {
        $("#rubberbandFormTag"+prNum).val("");
        $("#rubberbandFormComment"+prNum).val("");
        resetRubberband(prNum);
        return false;
    })

    // ----------------------------------------------------------------------
    // Form that needs to be added
    // ----------------------------------------------------------------------
    return "<div class='rubberbandFormDiv' id='rubberbandFormDiv"+prNum+"'><form id='rubberbandForm"+prNum+"' action='#' onsubmit='return false;'>" +
        "<table style=\"width: 100%;\">" +
        "<tr><td>Tag:</td><td><input style=\"width: 300px\"type='text' id='rubberbandFormTag"+prNum+"' /></td></tr>" +
        "<tr><td>Comment:</td><td><textarea style=\"width: 300px\" type='text' id='rubberbandFormComment"+prNum+"'></textarea></td></tr>" +
        "</table>" +
        "<button id='rubberbandFormSubmit"+prNum+"' class=\"btn btn-primary\" title=\"Create Section\">" +
        "<span class=\"glyphicon glyphicon-ok\"></span> Submit" +
        "</button>&nbsp;"+
        "<button id='rubberbandFormCancel"+prNum+"'class=\"btn btn-default\" title=\"Close\">" +
        "<span class=\"glyphicon glyphicon-remove\"></span> Close" +
        "</button>" +
        "</form></div>";

    // ----------------------------------------------------------------------
    // RUBBER BAND CODE
    // ----------------------------------------------------------------------
    function rubberbandStart(x, y, prNum) {
        console.log("start");
        window["mousedown" + prNum].x = x;
        window["mousedown" + prNum].y = y;

        window["rubberbandRectangle" + prNum].left	 = window["mousedown" + prNum].x;
        window["rubberbandRectangle" + prNum].top	 = window["mousedown" + prNum].y;
        window["rubberbandRectangle" + prNum].width	 = 0;
        window["rubberbandRectangle" + prNum].height = 0;

        resizeRubberbandDiv(prNum);
        moveRubberbandDiv(prNum);
        showRubberbandDiv(prNum);

        window["dragging" + prNum] = true;
    }

    function rubberbandStretch(x, y, prNum) {
        window["rubberbandRectangle" + prNum].left	 = x < window["mousedown" + prNum].x ? x : window["mousedown" + prNum].x;
        window["rubberbandRectangle" + prNum].top	 = y < window["mousedown" + prNum].y ? y : window["mousedown" + prNum].y;
        window["rubberbandRectangle" + prNum].width	 = Math.abs(x - window["mousedown" + prNum].x);
        window["rubberbandRectangle" + prNum].height = Math.abs(y - window["mousedown" + prNum].y);

        moveRubberbandDiv(prNum);
        resizeRubberbandDiv(prNum);
    }

    function rubberbandEnd(prNum) {
        var canvas = $("#rubberbandCanvas"+prNum)[0];
        var rubberbandFormDiv = $("#rubberbandFormDiv"+prNum)[0];
        var bbox = canvas.getBoundingClientRect();

        //rubberbandDiv.style.width = 0;
        //rubberbandDiv.style.height = 0;

        //hideRubberbandDiv();
        if ((window["rubberbandRectangle" + prNum].width > 0) && (window["rubberbandRectangle" + prNum].height > 0)) {
            rubberbandFormDiv.style.display = 'inline';
            rubberbandFormDiv.style.top	= (canvas.offsetTop + window["rubberbandRectangle" + prNum].top) + 'px';
            rubberbandFormDiv.style.left = (canvas.offsetLeft + window["rubberbandRectangle" + prNum].left + window["rubberbandRectangle" + prNum].width) + 'px';
        }
        window["dragging" + prNum] = false;
    }

    function moveRubberbandDiv(prNum) {
        var canvas = $("#rubberbandCanvas"+prNum)[0];
        var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];

        rubberbandDiv.style.top	= (canvas.offsetTop + window["rubberbandRectangle" + prNum].top) + 'px';
        rubberbandDiv.style.left = (canvas.offsetLeft + window["rubberbandRectangle" + prNum].left) + 'px';
    }

    function resizeRubberbandDiv(prNum) {
        var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];

        rubberbandDiv.style.width	= window["rubberbandRectangle" + prNum].width + 'px';
        rubberbandDiv.style.height = window["rubberbandRectangle" + prNum].height + 'px';
    }

    function showRubberbandDiv(prNum) {
        var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];
        var rubberbandFormDiv = $("#rubberbandFormDiv"+prNum)[0];

        rubberbandFormDiv.style.display = 'none';
        rubberbandDiv.style.display = 'inline';
    }

    function hideRubberbandDiv(prNum) {
        var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];
        var rubberbandFormDiv = $("#rubberbandFormDiv"+prNum)[0];

        rubberbandDiv.style.display = 'none';
        rubberbandFormDiv.style.display = 'none';
    }

    function resetRubberband(prNum) {
        var image = $("#rubberbandimage"+prNum)[0];
        var canvas = $("#rubberbandCanvas"+prNum)[0];
        var context = canvas.getContext('2d');
        var rubberbandDiv = $("#rubberbandDiv"+prNum)[0];

        context.clearRect(0, 0, context.canvas.width, context.canvas.height);
        window["page" + prNum].render({canvasContext: context, viewport: viewport});
        rubberbandDiv.style.width = 0;
        rubberbandDiv.style.height = 0;
        hideRubberbandDiv(prNum);

        $("#rubberbandFormTag"+prNum).val("");
        $("#rubberbandFormComment"+prNum).val("");
    }

    // associate preview with section
    function sectionCreated(tag, comment, sectionid, x, y, w, h, prNum) {
        // clone canvas to have a subimage
        var canvas = $("#rubberbandCanvas"+prNum)[0];
        var subcanvas = document.createElement("canvas");
        var cx = x * canvas.width;
        var cy = y * canvas.height;
        var cw = w * canvas.width;
        var ch = h * canvas.height;
        subcanvas.width = cw;
        subcanvas.height = ch;
        subcanvas.getContext("2d").putImageData(canvas.getContext('2d').getImageData(cx, cy, cw, ch), 0, 0);
        var imgdata = subcanvas.toDataURL("image/png");
        var binary = atob(imgdata.split(',')[1]);
        var array = [];
        for(var i = 0; i < binary.length; i++) {
            array.push(binary.charCodeAt(i));
        }
        var data = new FormData();
        data.append("File", new Blob([new Uint8Array(array)], {type: "image/png"}), "preview.png");

        // upload image as preview
        var request = window.jsRoutes.api.Previews.upload().ajax({
            type:        "POST",
            data:        data,
            contentType: false,
            processData: false,
        });
        request.done(function(response, textStatus, jqXHR) {
            previewCreated(tag, comment, sectionid, response.id, w, h, prNum);
        });
        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
        });
    }

    // tag and comment on section
    function previewCreated(tag, comment, sectionid, previewid, w, h, prNum) {
        var request = window.jsRoutes.api.Previews.uploadMetadata(previewid).ajax({
            type: 		 "POST",
            contentType: "application/json",
            data:		 JSON.stringify({
                section_id:  sectionid,
                width:       String(w),
                height:      String(h),
            }),
        });
        request.done(function(response, textStatus, jqXHR) {
        });
        request.fail(function (jqXHR, textStatus, errorThrown){
            console.error("The following error occured: " + textStatus, errorThrown);
        });

        // add tag to section
        if (tag != "") {
            request = window.jsRoutes.api.Sections.tag(sectionid).ajax({
                type:        "POST",
                contentType: "application/json",
                data:		 JSON.stringify({
                    tag: tag,
                }),
            });
            request.done(function (response, textStatus, jqXHR){
                var url = window.jsRoutes.controllers.Tags.search(tag).url;
                $('#tagList').append("<li><a href='" + url + "'>" + tag + "</a></li>");
                $('#tagField').val("");
            });
            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: " + textStatus, errorThrown);
            });
        }

        // add comment to section
        if (comment != "") {
            request = window.jsRoutes.api.Sections.comment(sectionid).ajax({
                type:        "POST",
                contentType: "application/json",
                data:		 JSON.stringify({
                    comment: comment,
                }),
            });
            request.done(function (response, textStatus, jqXHR){
            });
            request.fail(function (jqXHR, textStatus, errorThrown){
                console.error("The following error occured: " + textStatus, errorThrown);
            });
            $("#rubberbandFormComment"+prNum).val("");
        }
    }
}
