function template(scope) {
    return "<div><form submit=\"submitHandler()\" title=\"Title is " + scope.$get('formTitle') + " for " + scope.$get('formName') + "\"><formrow label=\"" + scope.$get('labelTitle') + "\"><input type=\"text\" model=\"someVar\"/></formrow></form><ul><li each=\"entries\" as=\"entry\"><span class=\"" + scope.$get('entry.className') + "\">" + scope.$get('entry.name') + "</span></li></ul></div>";
}