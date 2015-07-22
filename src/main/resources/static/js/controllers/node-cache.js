(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .factory('NodeCache', ['$http', NodeCache]);

    function NodeCache($http) {

        var cache = {};

        cache.nodes = {};
        cache.edges = [];

        var DRAFT_ID = "draft";

        function decorateWithRequiredProperties(node) {
            node.children = node.children || [];
            node.body = node.body || {
                author: {}
            };

            node.setBody = function(text) {
                node.body.body = text;
            };
            node.stopEditingBody = function() {
                if (node.doStopEditingBody) {
                    node.doStopEditingBody();
                }
            };
            node.linkChild = function(linkCallback) {
                if (node.doLinkChild) {
                    node.doLinkChild(linkCallback);
                }
            };

            return node;
        }

        cache.get = function(id) {
            return cache.nodes[id];
        };

        cache.getOrCreateNode = function(id) {
            if (cache.get(id)) {
                return cache.get(id);
            }

            var node = decorateWithRequiredProperties({id: id});

            cache.nodes[id] = node;
            return node;
        };

        cache.getOrCreateDraftNode = function() {

            if (cache.nodes[DRAFT_ID]) {
                return cache.nodes[DRAFT_ID];
            }

            // TODO: actually create something server-side
            var node = decorateWithRequiredProperties({
                id: DRAFT_ID,
                editingBody: true,
                editingTitle: true
            });

            cache.nodes[node.id] = node;
            return node;
        };

        cache.isDraftNode = function(node) {
            return node == cache.get(DRAFT_ID);
        }

        cache.saveDraftNode = function(successCallback, errorCallback) {
            var node = cache.get(DRAFT_ID);
            saveNode(node, function() {
                // Next time the draft node is requested, a fresh blank one should be built.
                cache.nodes[DRAFT_ID] = null;
                if (successCallback) {
                    successCallback(data); // This callback probably ought to change the URL to incorporate the new id.
                }
            }, errorCallback);
        };

        cache.createAndSaveNode = function(title, successCallback, errorCallback) {
            var node = {
                body: {title: title},
                children: []
            };
            saveNode(node, successCallback, errorCallback);
        };

        function saveNode(node, successCallback, errorCallback) {

            var links = node.children.map(function(child) {
                return child.id;
            });

            $http.post('/create',
                {
                    title: node.body.title,
                    body: node.body.body,
                    links: links
                })
                .success(function (data) {
                    cache.addOrUpdateNode(data);

                    if (successCallback) {
                        successCallback(data); // This callback probably ought to change the URL to incorporate the new id.
                    }
                })
                .error(function(err) {
                    if (errorCallback) {
                        errorCallback(err);
                    }
                });
        }

        cache.saveNodeEdit = function(node, successCallback, errorCallback) {

            var links = node.children.map(function(child) {
                return child.id;
            });

            // Iff the node is not a draft, this operation will produce a new draft node with a new id.
            $http.post('/editassertion',
                {
                    nodeId: node.id,
                    title: node.body.title,
                    body: node.body.body,
                    links: links
                })
                .success(function (data) {
                    var editedNode = cache.addOrUpdateNode(data);

                    if (editedNode.id != node.id) {
                        // The node had not been a draft, so a new one was produced to hold the edit. Provisionally,
                        // we will make the parent of the original point to the new draft, but because the draft is not
                        // published, this will not survive a page refresh.
                        $.each(cache.nodes, function(potentialParent) {
                            var index = potentialParent.children.indexOf(node);
                            if (index) {
                                potentialParent.children[index] = editedNode;
                            }
                        });
                    }

                    if (successCallback) {
                        successCallback(editedNode); // This callback probably ought to change the URL to incorporate the new id.
                    }
                })
                .error(function(err) {
                    if (errorCallback) {
                        errorCallback(err);
                    }
                });
        };

        cache.addOrUpdateNode = function(node) {
            var cachedNode = cache.get(node.id);
            if (cachedNode) {
                // Node is already in the cache, so perform updates

                if (node.body.majorVersion) {
                    // This is a good indicator that node's body is fully fleshed out and should be trusted.
                    cachedNode.body = node.body;
                } else {
                    cachedNode.body = cachedNode.body || node.body;
                    cachedNode.body.body = node.body.body;
                }

            } else {
                // node must be created and added to the cache
                cache.nodes[node.id] = decorateWithRequiredProperties(node);
            }

            return cache.get(node.id);
        };

        // nodes is a list of objects that have ids. Returns a map of the added nodes for further processing.
        cache.addNodesUnlinked = function(nodes) {
            var addedNodes = {};
            for (var i = 0; i < nodes.length; i++) {
                var node = cache.addOrUpdateNode(nodes[i]);
                addedNodes[node.id] = node;
            }
            return addedNodes;
        };

        function populateChildren(nodeMap, edgeList) {
            Object.keys(nodeMap).forEach(function (id) {
                var node = cache.get(id);
                node.children = [];
                var edges = edgeList.filter(function (el) {
                    return el[0] == id;
                });

                for (var j = 0; j < edges.length; j++) {
                    var child = cache.get(edges[j][1]);
                    node.children.push(child);
                    if (isInfinite(node)) {
                        // That's illegal! Remove that child.
                        node.children.splice(node.children.length - 1, 1);
                        console.log("Refusing to add child with id " + child.id + "!");
                    }
                }
            });
        }

        function isInfinite(node) {
            var closedSet = {};
            closedSet[node.id] = 1;
            return isInfiniteHelper(node, closedSet);
        }

        function isInfiniteHelper(node, closedSet) {
            for (var i = 0; i < node.children.length; i++) {
                var child = node.children[i];
                if (closedSet[child.id]) {
                    return true;
                } else {
                    closedSet[child.id] = 1;
                }
                var clonedSet = $.extend({}, closedSet);
                if (isInfiniteHelper(child, clonedSet)) {
                    return true;
                }
            }
            return false;
        }

        cache.fetchGraphForId = function(id, successCallback, errorCallback) {

            var rootNode = cache.get(id);

            if (rootNode && rootNode.hasFullGraph) {
                if (successCallback) {
                    successCallback(rootNode);
                }
            } else {
                $http.get('/graph', {params: {"rootId": id}}).success(function (data) {

                    var addedNodes = cache.addNodesUnlinked(data.nodes);
                    populateChildren(addedNodes, data.edges);
                    rootNode = cache.get(id);
                    rootNode.hasFullGraph = true;
                    if (successCallback) {
                        successCallback(rootNode);
                    }
                }).error(function(err) {
                    if (errorCallback) {
                        errorCallback(err);
                    }
                });
            }
        }

        cache.fetchNodeDetails = function(node) {
            $http.get('/detail', {params: {"id": node.body.id}}).success(function (data) {

                // Detail returns the current ArgumentNode in full detail,
                // the full comment tree for the current node,
                // and all the edges to link them together.

                // we're dealing with comments here!
                // TODO: make sure we're aggregating comments across everything within the major version and
                // indicating which minor version they are attributed to.
                var nodes = {};
                for (var i = 0; i < data.nodes.length; i++) {
                    var returnedId = data.nodes[i].id;
                    if (returnedId == node.body.id) {
                        // We already have this node cached; just fill in additional data
                        cache.get(node.id).body = data.nodes[i];

                        // This line is tricky. The node.id does NOT match returnedId. This will ultimately have the affect
                        // of attributing comments to the ArgumentNode when really they belong to the ArgumentBod(ies).
                        nodes[returnedId] = cache.get(node.id);
                    } else {
                        nodes[returnedId] = data.nodes[i];
                    }
                }

                Object.keys(nodes).forEach(function (id) {
                    var returnedNode = nodes[id];
                    returnedNode.comments = [];
                    var edges = data.edges.filter(function (el) {
                        return el[1] == id; // Use [1] here because comments point to their parents, so we want to match the tip of the arrow
                    });

                    for (var j = 0; j < edges.length; j++) {
                        returnedNode.comments.push(nodes[edges[j][0]]);
                    }
                });

            });
        };

        cache.getLinkChoices = function(bodyId, successCallback, errorCallback) {

            $http.get('/bodyChoices', {params: {"bodyId": bodyId}}).success(function (data) {
                successCallback(data.nodes);

            }).error(function(err) {
                if (errorCallback) {
                    errorCallback(err);
                }
            });
        }

        return cache;

    }

})();