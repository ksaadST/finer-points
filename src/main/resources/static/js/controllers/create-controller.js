(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .controller('CreateController', ['$scope', '$http', 'NodeCache', CreateController]);

    function CreateController($scope, $http, NodeCache) {

        $scope.starterNode = NodeCache.createDraftNode();

        $scope.submit = function () {
            $http.post('/create', {title: $scope.starterNode.title, body: $scope.starterNode.body, parentId: null})
                .success(function (data) {
                    alert("Success! " + data);
                })
                .error(function() {
                    // TODO: remember the request the user attempted, sign in via ajax,
                    // and let them try again.
                    window.location = '/signin';
                });
        };

        $scope.setText = function (text) {
            $scope.starterNode.body = text;
        };

        $scope.getSearchResults = function(query) {
            return $http.get('/search', {params: {query: query}})
                .then(function(response){

                    var bodyList = response.data;

                    return bodyList;
                });
        };

        $scope.searchResultSelected = function(a, b, c) {
            alert("" + a + b + c);
        };
    }

})();