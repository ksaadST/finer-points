require('../../sass/graph.scss');
require('../services/node-cache');
require('../services/user-service');
require('./vivagraph-directive');
require('./comments-directive');
require('./consumers-directive');
require('./node-editor-directive');
require('./vote-button-directive');
require('./markdown-directive');
require('./node-type-directive');

(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .directive('nodeGraph', nodeGraph)
        .controller('GraphController', GraphController);

    function nodeGraph() {
        return {
            restrict: "A",
            scope: {
                draftType: "="
            },
            template: require("../../partials/graph.html"),
            controller: 'GraphController',
            controllerAs: 'graphCtrl'
        }
    }

    function GraphController($scope, $routeParams, $location, $mdDialog, NodeCache, ToastService, UserService) {

        var self = this;

        self.publishableNodes = [];

        $scope.$watch('draftType', function() {
            var draftType = $scope.draftType;

            if (draftType) {

                if (self.rootNode) {
                    self.rootNode.type = draftType;
                } else {

                    var starterNode = NodeCache.getOrCreateDraftNode(draftType);
                    self.rootNodes = [starterNode];
                    self.rootNode = self.rootNodes[0];
                    self.draftNodes = [starterNode];
                    self.enterEditMode(starterNode);
                    starterNode.isSelected = true;
                }

                $scope.$broadcast("rootData", self.rootNode);
            }
        });


        function setHighlighted(node) {
            if (self.highlightedNode !== node) {
                self.highlightedNode = node;
                $scope.$broadcast("nodeHighlighted", node);
            }
        }

        $scope.$on("nodeTapped", function(e, node) {
            spotlightNode(node);
        });

        function spotlightNode(node) {
            setHighlighted(node);
            revealChild(self.rootNode, node);
            ensureDetail(node);
            node.isSelected = true;
        }

        self.spotlightNode = function (node) {
            spotlightNode(node);
        };

        $scope.$on("nodeSaved", function(e, node) {
            self.problemReport = buildProblemReport(self.rootNode);
        });

        $scope.$on("canceledEdit", function(e, node) {
            $scope.$broadcast("rootData", self.rootNode);
        });

        $scope.$on("nodeLinkClick", function(e, majorVersionId) {
            var scope = e.targetScope;
            while (scope && !scope.node) {
                scope = scope.$parent;
            }
            var parentNode = scope ? scope.node : self.rootNode;
            for (var i = 0; i < parentNode.children.length; i++) {
                if (parentNode.children[i].body.majorVersion.stableId === majorVersionId) {
                    spotlightNode(parentNode.children[i]);
                    return;
                }
            }
        });

        function revealChild(node, child) {
            if (node === child) {
                return true;
            }

            var holdsChild = false;

            $.each(node.children, function(index, n) {
                 if (revealChild(n, child)) {
                     holdsChild = true;
                 }
            });

            if (holdsChild) {
                revealChildren(node);
                return true;
            }

            return false;
        }

        self.enterEditMode = function (node) {
            if (node.body.public) {

                if (node !== self.rootNode) {
                    ToastService.error("Please navigate to the node before attempting to edit.");
                }

                var alias = UserService.getActiveAlias();

                NodeCache.makeDraft(node, alias, function(draftNode, data) {

                    $location.path("/graph/" + data.graph.rootStableId);

                    self.enterEditMode(draftNode);
                    draftNode.isSelected = true;

                }, function (err) {
                    ToastService.error(err.message);
                });

            } else {
                node.inEditMode = true;

                // Proactively get the children too because we might need their majorVersion id's during editing
                $.each(node.children, function(index, child) {
                    ensureDetail(child);
                });
            }
        };

        if ($routeParams && $routeParams.rootStableId) {

            self.rootStableId = $routeParams.rootStableId;

            NodeCache.fetchGraphForId($routeParams.rootStableId, function() {
                self.rootNode = NodeCache.getByStableId($routeParams.rootStableId);
                self.rootNodes = [self.rootNode]; // We have this in array form because it's handy for kicking off the angular template recursion.

                var currentNode = self.rootNode;
                while (currentNode.children && currentNode.children.length === 1) {
                    revealChildren(currentNode);
                    currentNode = currentNode.children[0];
                }

                self.problemReport = buildProblemReport(self.rootNode);
                $scope.$broadcast("rootData", self.rootNode);
            });
        }

        function revealChildren(node) {
            node.showChildren = true;

            // fetch body for any children that are leaf nodes
            if (node.children) {
                $.each(node.children, function (index, child) {
                    if (child.type === 'source' || child.type === 'subject') {
                        ensureDetail(child);
                        child.isSelected = true;
                    }
                });
            }
        }

        self.hasChild = function (node) {
            return node.children && node.children.length;
        };

        // Selection stuff. I have functions for these even though they could
        // fit in the html because in html there are a bunch of nested scopes
        // and access to $scope there is weird.

        self.isSelected = function (node) {
            return node.isSelected;
        };

        self.getRenderType = function(node) {
            return node.isLeaf() ? 'url' : 'markdown';
        };

        self.toggleSelect = function (node) {
            node.isSelected = !node.isSelected;
            if (node.isSelected) {
                ensureDetail(node);
            }

            return true;
        };

        self.toggleChildren = function (node) {
            if (node.showChildren) {
                node.showChildren = false;
            } else {
                revealChildren(node);
            }
        };

        self.navigateToNode = function (node) {
            // Change the page url and reload the graph. All the UI state should stay the same because
            // the nodes are in the NodeCache.
            $location.path("/graph/" + node.stableId);
        };

        self.authorizedForEdit = function (node, parent) {
            // Currently, I only want to allow child editing if the children are already private,
            // or if the child's immediate parent is private.
            // This is because I don't intend to mess with draft propagation anymore.
            // If you want to edit a public child, the user needs to navigate to it first,
            // or start edit its parent.
            return UserService.getUser() && (node === self.rootNode || !node.body.public || (parent && !parent.body.public));
        };

        function ensureDetail(node) {
            if (!hasFullDetail(node)) {
                NodeCache.getFullDetail(node.stableId);
            }
        }

        function hasFullDetail(node) {
            return node.body && node.body.author && node.body.author.stableId;
        }

        self.setBody = function(node, text) {
            node.body.body = text;
        };

        self.readyToPublish = function(node) {
            return (!node.body.public) && allowsPublish(node, {});
        };

        self.publishNode = function(node) {
            var publishableSet = {};
            if (allowsPublish(node, publishableSet)) {

                // Problem: draft -> draft -> published
                // Publish the middle one
                // Now the middle one appears to have no child and no author.
                // Upon refresh, it has correct child and author.

                NodeCache.publishNode(node, function(quickGraph) {

                    var rootNode = self.rootNode;

                    if (node === rootNode) {
                        $location.path("/graph/" + quickGraph.rootStableId); // Change url back to public version
                    }
                });
            } else {
                // TODO: display an error
            }
        };

        self.discardDraft = function() {
            NodeCache.discardDraft(self.rootNode, function() {
                $location.path("/");
            });
        };

        function allowsPublish(node) {
            return self.problemReport && !self.problemReport.problemNodes[node];
        }

        function buildProblemReport(node) {

            var visitedNodes = {};

            var problemReport = { messages: [], problemNodes: {}};

            buildReport(node);

            return problemReport;

            function buildReport(node) {

                var hasPublishBlockers = false;

                if (visitedNodes[node.id] || node.body.draft === false) {
                    return false;
                }

                visitedNodes[node.id] = 1;

                if (!node.body.title) {
                    problemReport.messages.push({message: "You need to give your card a title.", node: node});
                    hasPublishBlockers = true;
                }

                if (node.body.title === 'Untitled') {
                    problemReport.messages.push({message: 'You need to write a title other than "Untitled."', node: node});
                    hasPublishBlockers = true;
                }

                if (node.type === "source") {
                    if (!node.body.url) {
                        problemReport.messages.push({message: "You need a URL for your source node.", node: node});
                        hasPublishBlockers = true;
                    }
                } else if (node.type === "subject") {
                    if (!node.body.url) {
                        problemReport.messages.push({message: "You need a URL for your subject node.", node: node});
                        hasPublishBlockers = true;
                    }
                } else if (node.type === "interpretation") {
                    if (!node.body.body) {
                        problemReport.messages.push({message: "You need some text in your interpretation.", node: node});
                        hasPublishBlockers = true;
                    }

                    if (!node.children.length) {
                        problemReport.messages.push({message: "You need to attach a source card support your interpretation.", node: node});
                        hasPublishBlockers = true;
                    }
                    else {
                        hasPublishBlockers = buildReport(node.children[0]) || hasPublishBlockers;
                    }
                } else if (node.type === "assertion") {
                    if (!node.body.body) {
                        problemReport.messages.push({message: "You need some text in your opinion.", node: node});
                        hasPublishBlockers = true;
                    }

                    if (!node.children.length) {
                        problemReport.messages.push({message: "You need to attach cards to support your opinion. On the card, click Edit, then Attach Card.", node: node});
                        hasPublishBlockers = true;
                    }

                    for (var i = 0; i < node.children.length; i++) {
                        hasPublishBlockers = buildReport(node.children[i]) || hasPublishBlockers;
                    }
                }

                if (hasPublishBlockers) {
                    problemReport.problemNodes[node] = 1;
                }
                return hasPublishBlockers;
            }
        }

    }

})();