(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .directive('vivaGraph', ['$routeParams', '$modal', 'NodeCache', vivaGraph]);

    function vivaGraph($routeParams, $modal, NodeCache) {
        return {
            restrict: "A",
            scope: {
                rootNode: "="
            },
            templateUrl: "partials/viva-graph.html",
            link: function (scope) {
                setupEventHandlers(scope);
            }
        }
    }



    function setupEventHandlers(scope) {

        var graph = Viva.Graph.graph();

        var layout = Viva.Graph.Layout.forceDirected(graph, {
            springLength : 40,
            springCoeff : 0.0008,
            dragCoeff : 0.03,
            gravity : -1.2
        });

        var graphics = Viva.Graph.View.svgGraphics();
        graphics.node(function(node) {
            // The function is called every time renderer needs a ui to display node
            var circle = Viva.Graph.svg('circle', {
                r: node.data.isRoot? 18 : 14,
                stroke: '#000',
                fill: node.data.color
            });

            circle.addEventListener('click', function () {
                layout.pinNode(node, true);
            });

            return circle;

        }).placeNode(function(nodeUI, pos){
            nodeUI.attr('cx', pos.x).attr('cy', pos.y);
        });


        var renderer = Viva.Graph.View.renderer(graph, {
            container  : document.getElementById('viva-container'),
            graphics: graphics,
            layout: layout,
            interactive: 'node drag'
        });
        renderer.run();

        if (scope.rootNode) {
            addVivaNodesRecursive(scope.rootNode, null, graph, {});
        }

        scope.$on("rootData", function(event, rootNode) {
            addVivaNodesRecursive(rootNode, null, graph, {});
        });

        scope.$on("nodeAdded", function(event, parent, child) {
            addVivaNodesRecursive(child, parent, graph, {});
        });

        scope.$on("edgeAdded", function(event, parent, child) {
            graph.addLink(parent.id, child.id);
        });

        scope.$on("edgeRemoved", function(event, parent, child) {

            var linkToRemove = null;

            graph.forEachLinkedNode(parent.id, function(linkedNode, link){
                if (linkedNode.id === child.id) {
                    linkToRemove = link;
                }
            });

            graph.removeLink(linkToRemove);
        });
    }

    function getColor(node) {
        if (node.getType() === 'assertion') {
            return "#8888DD";
        }
        if (node.getType() === 'interpretation') {
            return "#88DD88";
        }
        if (node.getType() === 'source') {
            return "#DD8888";
        }

        return "#000000";
    }


    function addVivaNodesRecursive(node, parent, graph, addedNodes) {

        if (!addedNodes[node.id]) {


            graph.addNode(node.id, {
                node : node,
                color: getColor(node),
                isRoot: !parent
            });

            addedNodes[node.id] = 1;

            for(var i = 0; i < node.children.length; i++) {
                addVivaNodesRecursive(node.children[i], node, graph, addedNodes);
            }
        }

        if (parent) {
            graph.addLink(parent.id, node.id);
        }

    }

})();