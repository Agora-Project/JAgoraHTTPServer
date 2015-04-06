//Copyright (C) 2015 Agora Communication Corporation
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License along
//    with this program; if not, write to the Free Software Foundation, Inc.,
//    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
    
        var port = 27387;
        
        var force; //this variable will contain the force layout object.
        
        //these arrays will contain the nodes and links that are i the appropriate 
        // format to be understood by the force layout.
        var jsgraph = {
            nodes : [],
            links : []
        };   
        
        var modeList = {
            login : 1,
            reply : 2,
            register: 3,
            moderate: 4
        };
        
        //this variable is for storing the list of posts that a new post is in reply to.
        var targets = [];
        
        var bridge;
        
        
        // Global variables
        var JAVA_READY_FLAG = false;

        // Get the applet object
        function getJAgoraBridge(){
                return document.getElementById('JAgoraJSBridge');
        }
        // Applet reports it is ready to use
        function javaSocketBridgeReady() {
                bridge = getJAgoraBridge();
                bridge.startLib();
                JAVA_READY_FLAG = true;
                loadGraph(bridge.getArgumentByID(bridge.findArgument(43)));
        }
        
        function setAppMode(mode) {
            if (mode == modeList.login) {
                d3.select("#LoginPanel").classed("inactive", false);
                d3.select("#ReplyPanel").classed("inactive", true);
                d3.select("#RegisterPanel").classed("inactive", true);
                d3.select("#ModeratorPanel").classed("inactive", true);
            } else if (mode == modeList.reply) {
                d3.select("#LoginPanel").classed("inactive", true);
                d3.select("#ReplyPanel").classed("inactive", false);
                d3.select("#RegisterPanel").classed("inactive", true);
                d3.select("#ModeratorPanel").classed("inactive", true);
            } else if (mode == modeList.register) {
                d3.select("#LoginPanel").classed("inactive", true);
                d3.select("#ReplyPanel").classed("inactive", true);
                d3.select("#RegisterPanel").classed("inactive", false);
                d3.select("#ModeratorPanel").classed("inactive", true);
            } else if (mode == modeList.moderate) {
                d3.select("#LoginPanel").classed("inactive", true);
                d3.select("#ReplyPanel").classed("inactive", true);
                d3.select("#RegisterPanel").classed("inactive", true);
                d3.select("#ModeratorPanel").classed("inactive", false);
            }
        }
        
        //This function registers a new user for the forum.
        function register() {
            if (d3.select("#registerPasswordField").node().value !== 
                d3.select("#confirmPasswordField").node().value) {
                alert("Passwords do not match.");
                return;
            }
            if (JAVA_READY_FLAG === true) {
                    bridge.register(d3.select("#registerUsernameField").node().value, 
                                    d3.select("#registerPasswordField").node().value,
                                    d3.select("#registerEmailField").node().value);
                    setAppMode(modeList.reply);
                    d3.select("#registerUsernameField").property("value", "");
                    d3.select("#registerPasswordField").property("value", "");
                    d3.select("#registerEmailField").property("value", "");
                    d3.select("#confirmPasswordField").property("value", "");
            } else alert("Applet not yet ready.");
        }
        
        // This fuction logs us in and loads the forum hub.
        function logIn() {
            if (JAVA_READY_FLAG === true) {
                if (bridge.logIn(d3.select("#usernameField").node().value, d3.select("#passwordField").node().value)) {
                    setAppMode(modeList.reply);
                    d3.select("#usernameField").property("value", "");
                    d3.select("#passwordField").property("value", "");
                    if (bridge.isModerator())
                        d3.select("#moderatorButton").classed("inactive", false);
                }
                
            }
            else alert("Applet not yet ready.");
        }
        
        //this function is for moderators. It lets them delete spam posts.
        
        function deletePosts() {
            bridge.deletePosts(targets);
            for (post in targets) removePost(post);
            targets = [];
            refreshSVG();
        }
            
        
        //This function loads the arguments and attacks that are directly related to a argument.
        
        function loadNodes(d) {
            loadGraph(bridge.getArgumentByID(d))
        }
        
        //This function clears the svg, so we can draw more stuff in it.
        
        function clearSVG() {
            svg.selectAll("*").remove();
        }
        
        //this funcion clears the local javascript graph.
        
        function clearGraph() {
            jsgraph.nodes = [];
            jsgraph.links = [];
        }
        
        //this function adds a new post that you can fill with text, connect to 
        //existing posts, and submit.
        
        function newPost() {
            if (JAVA_READY_FLAG === true && targets.length > 0) {
                var post = bridge.newArgument(d3.select("#titleField").node().value,
                                   d3.select("#bodyField").node().value, targets);
                loadNodes(post);
                d3.select("#titleField").property("value", "");
                d3.select("#bodyField").property("value", "");
                targets = [];
            }
            
        }
        
        //this function removes a post from the local javascript graph, reloads 
        //the links (Which should eliminate any attacks directly related to said 
        //argument, but doesn't right now), and then re-does the svg display.
        
        function removePost(d) {
            var i;
            for (i = 0; i < jsgraph.links.length; i++) {
                if (jsgraph.links[i].source.post.getID().equals(d.post.getID())) {
                    jsgraph.links.splice(i,1);
                    i--;
                }
                if (jsgraph.links[i].target.post.getID().equals(d.post.getID())) {
                    jsgraph.links.splice(i,1);
                    i--;
                }
            }
            
            for (i = 0; i < jsgraph.nodes.length; i++) {
                if (jsgraph.nodes[i].post.getID().equals(d.post.getID())) {
                    jsgraph.nodes.splice(i,1);
                    break;
                }
            }
            
            refreshSVG();
        }
        
        function sizeArgument(d) {
            var size = [150, 60], characters = 0;
            characters = d.post.getText().length;
            return size;
        }
        
        //this function load the graph and constructs a visual representation.
        
        function refreshSVG() {
            clearSVG();
            
            force = d3.layout.force()
                 .nodes(jsgraph.nodes)
                 .links(jsgraph.links)
                 .size([1000, 540])
                 .linkStrength(0.3)
                 .gravity(0.005)
                 .charge([-100])
                 .chargeDistance([300])
                 .start();
            
            edges = svg.selectAll("line")
                .data(jsgraph.links);
        
            edges.exit().remove();
        
            edges.enter()
                .append("line")
                .style("stroke", "rgb(255,0,0)");
        
            var numPosts = 0;
        
            var nodes = svg.selectAll("g")
                .data(jsgraph.nodes);
                
            
        
            nodes.exit().remove();
            
            nodes.enter()
                .append("g")
                .attr("transform", function(d) {return "translate("+1000+","+1000+")";});;
        
            svg.selectAll("g")
                .attr("id", function(d) {
                    return "group" + numPosts++;
                });
        
            
            numPosts = 0;
             var rects = svg.selectAll("g")
                .append("rect")
                .attr("width", function(d) {
                    return sizeArgument(d)[0];
                }).attr("height", function(d) {
                    return sizeArgument(d)[1];
                }).attr("id", function(d) { return "rect" + numPosts++;})
                .call(force.drag);
        
            var titles = svg.selectAll("g")
                .append("text")
                .text(function(d) {return d.post.getContent().get("Title");})
                .attr("font-family", "sans-serif")
                .attr("font-size", "11px");
        
            var i;
            numPosts = 0;
            var texts = svg.selectAll("g")
                .append("text")
                .text(function(d) {return d.post.getText();})
                .attr("font-size", "11px")
                .attr("font-family", "sans-serif")
                .attr("fill", "white")
                .attr("id", function(d) { return "text" + numPosts++;});
        
            for (i = 0; i < numPosts; i++) {
                d3plus.textwrap()
                    .container(d3.select("#text" + i))
                    .width(d3.select("#rect" + i).node().width.baseVal.value)
                    .draw();
            }
        
            var expandButtons = svg.selectAll("g")
                .append("rect")
                .attr("x", function(d) {
                    return d3.select("#rect" + d.index).node().width.baseVal.value -30;
                })
                .attr("y", function(d) {
                    return d3.select("#rect" + d.index).node().height.baseVal.value -10;
                })
                .attr("width", 30)
                .attr("height", 10)
                .style("fill", "blue")
                .on("click", function(d) {
                    loadNodes(d.post.getID());
                });
            
            numPosts = 0;
            var replyButtons = svg.selectAll("g")
                    .append("rect")
                    .attr("y", function(d) {
                        return d3.select("#rect" + d.index).node().height.baseVal.value -10;
                    })
                    .attr("width", 30)
                    .attr("height", 10)
                    .attr("id", function(d) { return "replyButton" + numPosts++;})
                    .style("fill", function(d) {
                        for (i = 0; i < targets.length; i++) {
                            if (targets[i].getID().equals(d.post.getID())) {
                                return "white";
                            }
                        }
                        
                        return "green";             
                    })
                    .on("click", function (d) {
                        var index = -1;
                        for (i = 0; i < targets.length; i++) {
                            if (targets[i].getID().equals(d.post.getID())) {
                                index = i;
                                break;
                            }
                        }
                        if (index == -1) {
                            targets.push(d.post);
                            d3.select("#replyButton" + d.index)
                            .style("fill", "white");
                        } else {
                            targets.splice(index, 1);
                            d3.select("#replyButton" + d.index)
                            .style("fill", "green");
                        }
                    });
                    
            var closeButtons = svg.selectAll("g")
                    .append("circle")
                    .attr("cx", function(d) {
                        return d3.select("#rect" + d.index).node().width.baseVal.value;
                    })
                    .attr("r", 10)
                    .style("fill", "red")
                    .on("click", removePost);
            
            force.on("tick", function() {

                edges.attr("x1", function(d) { return d.source.x + d3.select("#rect" + d.source.index).node().width.baseVal.value/2})
                     .attr("y1", function(d) { return d.source.y + d3.select("#rect" + d.source.index).node().height.baseVal.value/2; })
                     .attr("x2", function(d) { return d.target.x + d3.select("#rect" + d.target.index).node().width.baseVal.value/2; })
                     .attr("y2", function(d) { return d.target.y + d3.select("#rect" + d.target.index).node().height.baseVal.value/2; });
             
                for(i = 0; i < jsgraph.links.length; i++) {
                    var targy = jsgraph.nodes[jsgraph.links[i].target.index].y;
                    var sorcy = jsgraph.nodes[jsgraph.links[i].source.index].y;
                    if (sorcy - targy < 80) {
                        jsgraph.nodes[jsgraph.links[i].target.index].y -= 1;
                        jsgraph.nodes[jsgraph.links[i].source.index].y += 1;
                    }
                }
                nodes.attr("transform", function(d) {return "translate("+d.x+","+d.y+")";});

            })
            .stop()
            .linkDistance(function(d) {
                var rectw = d3.select("#rect" + d.source.index).node().width.baseVal.value;
                var recth = d3.select("#rect" + d.source.index).node().height.baseVal.value;
                var linkLength = Math.sqrt((rectw * rectw) + (recth * recth));
                rectw = d3.select("#rect" + d.target.index).node().width.baseVal.value;
                recth = d3.select("#rect" + d.target.index).node().height.baseVal.value;
                linkLength += Math.sqrt((rectw * rectw) + (recth * recth));
                linkLength *= .75;
                return linkLength;
            })
            .start();
        }
        
        //this function loads takes the links and turns them into javascript 
        //data that the force layout can make use of.
        
        function loadLinks(links) {
            var i, j, edge, post;
            mainloop:
            for (i = 0; i < links.length; i++) {
                edge = links[i];
                var target = null, origin = null;
                //ensure there are no duplicate links.
                for (j = 0; j < jsgraph.links.length; j++) {
                    if (edge.getTarget().getID().equals(jsgraph.links[j].target.post.getID()) &&
                        edge.getOrigin().getID().equals(jsgraph.links[j].source.post.getID()))
                        continue mainloop;
                }
                
                for (j = 0; j < jsgraph.nodes.length; j++) {
                    post = jsgraph.nodes[j].post;
                    
                    if (post.getID().equals(edge.getTarget().getID())) {
                        target = jsgraph.nodes[j];
                    }
                    if (post.getID().equals(edge.getOrigin().getID())) {
                        origin = jsgraph.nodes[j];
                    }
                    if (target != null && origin != null) {
                        jsgraph.links.push({source : origin, target : target});
                        break;
                    }
                }
                
            }
        }
        
        //this function takes a JAgoraGraph object, copies the data from it, and 
        //then calls loadLinks() and refreshSVG().
        
        function loadGraph(graph) {
            
            
            var posts = graph.getNodes();
            var newLinks = graph.getAttacks();
            var edge, i, post, j, foundNode;
            
            for (i = 0; i < posts.length; i++) {
                foundNode = false;
                for (j = 0; j < jsgraph.nodes.length; j++) {
                    if (jsgraph.nodes[j].post.getID().equals(posts[i].getID())) {
                        foundNode = true;
                        break;
                    }
                }
                if (!foundNode) jsgraph.nodes.push({post : posts[i]});
            }
            
            loadLinks(newLinks);
            
            refreshSVG();
            
        }
        
