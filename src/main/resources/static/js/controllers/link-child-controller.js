(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .controller('LinkChildController', ['$scope', '$http', '$modalInstance', 'NodeCache', 'linkCallback', LinkChildController]);

    function LinkChildController($scope, $http, $modalInstance, NodeCache, linkCallback) {
        $scope.getSearchResults = function(query) {
            return $http.get('/search', {params: {query: query}})
                .then(function(response){

                    var bodyList = response.data;
                    return bodyList;
                });
        };

        $scope.searchResultSelected = function(bodyNode) {
            NodeCache.getLinkChoices(bodyNode.id, function(nodes) {
                // For now, get the most recent node for linking.
                sortNodesByVersion(nodes);

                $scope.chosenNode = nodes[nodes.length - 1];

            }, function(err) {
                alert("There was an error: " + err);
            });

        };

        function sortNodesByVersion(nodes) {
            nodes.sort(function(n, m) {
                var nVersion = n.version.split(".");
                var mVersion = m.version.split(".");

                return nVersion[0] - mVersion[0] || nVersion[1] - mVersion[1] || nVersion[2] - mVersion[2];
            });
        }

        $scope.ok = function() {
            $modalInstance.close();
            linkCallback($scope.chosenNode);
        }

        $scope.cancel = function() {
            $modalInstance.close();
        }



    }

})();