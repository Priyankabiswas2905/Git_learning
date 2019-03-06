function addDefinition(data, pageURL, spaceId){
  var url = jsRoutes.api.Metadata.addDefinition()
  if(spaceId != "") {
    url = jsRoutes.api.Metadata.addDefinitionToSpace(spaceId);
  }
  if($(".definitionAction").text().indexOf( "Edit") > -1) {
    var id = $('.definitionAction').attr('id');
    var editUrl = jsRoutes.api.Metadata.editDefinition(id);
    if(spaceId != "") {
    	editUrl = jsRoutes.api.Metadata.editDefinition(id, spaceId);
    }
    var request = editUrl.ajax({
      type: 'POST',
      data: JSON.stringify(data),
      contentType: "application/json"
    });

    request.done(function (response, textStatus, jqXHR) {
      if (textStatus == "success") {
        window.location.href = window.location.href.split('#')[0];
      }
    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
      notify("ERROR: " + jqXHR.responseJSON + " Metadata Definition not edited.", "error");
    });
  }
  else {
    var request = url.ajax({
      type: 'POST',
      data: JSON.stringify(data),
      contentType: "application/json"
    });

    request.done(function (response, textStatus, jqXHR) {
      if (textStatus == "success") {
        window.location.href = window.location.href.split('#')[0];
      }

    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
      notify("ERROR: " + jqXHR.responseJSON + " Metadata Definition not added.", "error");
    });
  }
}

function editDefinition(id, json, element) {
  reset();
  $(".definitionAction").text("Edit");
  $(".definitionActionButton").text(" Save");
  $(".glyphicon-plus").attr("class", "glyphicon glyphicon-send");
  $(".definitionAction").attr("id",id);
  json = JSON.parse(json);
  if (json.label) {
    $("#label").val(json.label);
  }
  if (json.description) {
    $("#description").val(json.description);
  }
  if (json.uri) {
    $("#uri").val(json.uri);
  }
  if (json.type) {
    $("#type").val(json.type).change();
  }
  if (json.definitions_url) {
    // Grab the origin + default path from the URL
    var origin = window.location.href.split('/api')[0];
    if (json.definitions_url.indexOf(origin) === -1) {
      $("#defined_by").val("url");
      $("#defined_by_list").hide();
      $("#definitions_list").val('');
      $("#defined_by_url").show();
      $("#definitions_url").val(json.definitions_url);
    } else {
      $("#defined_by").val("list");
      $("#defined_by_url").hide();
      $("#definitions_url").val('');
      $("#defined_by_list").show();
      var request = $.ajax({
        url: json.definitions_url,
        type: 'GET',
        contentType: "application/json"
      });
      request.done(function (response, textStatus, jqXHR) {
        $("#definitions_list").val(response.join("\n"));
        $("#vocabulary_id").val(getVocabularyIdFromUrl(json.definitions_url));
      });
      request.fail(function (jqXHR, textStatus, errorThrown) {
        notify("ERROR: " + jqXHR.responseJSON + " Standard Vocabulary not found.", "error");
      });
    }
  }
  if (json.query_parameter) {
    $("#query_parameter").val(json.query_parameter);
  }

  $("#cancelButton").show();
}

function reset(element) {
  element = element || $("#cancelButton");
  $(':input').val('');
  $(element).hide();
  $("#validationResults").empty();
  $('.definitionAction').text('Add');
  $(".definitionActionButton").text("Add");
  $(".glyphicon-send").attr("class", "glyphicon glyphicon-plus");
}

function getVocabularyIdFromUrl(url) {
    // Grab the origin + default path from the URL
    var origin = window.location.href.split('/api')[0];

    // Strip out the hostname to make things easier
    var suffix = url.replace(origin, '');

    // Suffix takes the form /api/standardvocab/:id/terms - we just want the ID
    return suffix.split('/')[3];
}

function createVocabulary(terms) {
  console.log("Creating new vocabulary...");

  var url = jsRoutes.api.Metadata.createVocabulary();
  return new Promise(function (resolve, reject) {
    var request = url.ajax({
      type: 'POST',
      data: JSON.stringify(terms),
      contentType: "application/json"
    });


    request.done(function (response, textStatus, jqXHR) {
      if (textStatus == "success") {
        resolve(response);
      }
    });
    request.fail(function (jqXHR, textStatus, errorThrown) {
      notify("ERROR: " + jqXHR.responseJSON + " Standard Vocabulary not added.", "error");
      reject(errorThrown);
    });
  });
}

function updateVocabulary(id, terms) {
  console.log("Updating vocabulary (" + id + ")...");
  var url = jsRoutes.api.Metadata.updateVocabulary(id);

  return new Promise(function (resolve, reject) {
    var request = url.ajax({
      type: 'PUT',
      data: JSON.stringify(terms),
      contentType: "application/json"
    });

    request.done(function (response, textStatus, jqXHR) {
      if (textStatus == "success") {
        resolve(response)
      }
    });

    request.fail(function (jqXHR, textStatus, errorThrown) {
      notify("ERROR: " + jqXHR.responseJSON + " Standard Vocabulary not updated.", "error");
      reject(errorThrown)
    });
  });
}

function deleteVocabulary(id) {
    console.log("Deleting vocabulary (" + id + ")...");
    var url = jsRoutes.api.Metadata.deleteVocabulary(id);

    return new Promise(function (resolve, reject) {
        var request = url.ajax({
            type: 'DELETE',
            contentType: "application/json"
        });

        request.done(function (response, textStatus, jqXHR) {
            if (textStatus == "success") {
                resolve(response)
            }
        });

        request.fail(function (jqXHR, textStatus, errorThrown) {
            notify("ERROR: " + jqXHR.responseJSON + " Standard Vocabulary not deleted.", "error");
            reject(errorThrown)
        });
    });
}