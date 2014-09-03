(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
  $(Configuration.tab).append("<br/><p><b>Important: </b>Do not use this previewer on computers using a second screen, as there are some issues. Use the Quicktime-based one instead.</p>");
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = "http://popcornjs.org/code/dist/popcorn-complete.min.js";
    
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  
  $(Configuration.tab).append("<br/>");
  $(Configuration.tab).append(			  
     "<video width='750px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );
  
}(jQuery, Configuration));
