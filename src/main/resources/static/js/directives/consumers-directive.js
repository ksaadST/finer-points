
require('../services/toast-service');
require('./node-list-directive');

(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .directive('nodeConsumers', nodeConsumers)
        .controller('NodeConsumersController', NodeConsumersController);
        

    function nodeConsumers() {
        return {
            restrict: "A",
            scope: {
                node: "="
            },
            template: require('../../partials/consumers.html'),
            controller: "NodeConsumersController",
            controllerAs: "consumersCtrl"
        }
    }

    function NodeConsumersController($scope, $location, NodeCache, UserService) {

        var self = this;
        self.nodes = $.map($scope.node.parents, function(val) {return val;});

        self.newConsumer = function() {
            NodeCache.createNodeWithSupport($scope.node, UserService.getActiveAlias(), function(newNode) {
                $location.path("/graph/" + newNode.stableId);
            });
        };

        self.canCreateConsumer = function() {
            return !!UserService.getUser();
        }

    }

})();