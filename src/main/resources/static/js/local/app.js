(function() {
    'use strict';

    angular.module('nodeStandControllers', []);

    var nodeStandApp = angular.module('nodeStandApp', [
        'ngRoute',
        'markdown',
        'ui.bootstrap',
        'nodeStandControllers'
    ]);

    nodeStandApp.config(['$routeProvider',
        function ($routeProvider) {
            $routeProvider.
                when('/', {
                    templateUrl: 'partials/gateway.html',
                    controller: 'GatewayController'
                }).
                when('/login', {
                    templateUrl: 'partials/signin.html',
                    controller: 'LoginController'
                }).
                when('/graph', {
                    templateUrl: 'partials/node-menu.html',
                    controller: 'NodeMenuController'
                }).
                when('/graph/:rootStableId', {
                    templateUrl: 'partials/explorer.html'
                }).
                when('/create', {
                    templateUrl: 'partials/create.html'
                }).
                when('/graphDiagnostic/:rootId', {
                    templateUrl: 'partials/graphDiagnostic.html',
                    controller: 'GraphController'
                }).
                when('/d/:id', {
                    templateUrl: 'partials/detail.html',
                    controller: 'DetailController'
                }).
                otherwise({
                    redirectTo: '/'
                });
        }]);

    nodeStandApp.run(['$rootScope', 'UserService', function ($rootScope, UserService) {
        $rootScope.user = UserService.getUser();

        UserService.subscribeSuccessfulLogin($rootScope, function () {
            $rootScope.user = UserService.getUser();
        });
    }]);

})();