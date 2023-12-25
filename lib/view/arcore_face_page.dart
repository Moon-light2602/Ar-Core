import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ArcoreFacePage extends StatefulWidget {
  const ArcoreFacePage({Key? key}) : super(key: key);

  @override
  State<ArcoreFacePage> createState() => _ArcoreFacePageState();
}

class _ArcoreFacePageState extends State<ArcoreFacePage> {
  @override
  Widget build(BuildContext context) {
    const String viewType = 'ArCoreFaceViewType';
    final Map<String, dynamic> creationParams = <String, dynamic>{};

    return Scaffold(
        appBar: AppBar(
          title: const Text("Native View"),
        ),
        body: Column(
          children: [
            Container(
                width: MediaQuery.of(context).size.width,
                color: Colors.red,
                child: const Text(
                  "Text Flutter",
                  style: TextStyle(fontSize: 32),
                  textAlign: TextAlign.center,
                )),
            Expanded(
              child: AndroidView(
                viewType: viewType,
                layoutDirection: TextDirection.ltr,
                creationParams: creationParams,
                creationParamsCodec: const StandardMessageCodec(),
              ),
            )
          ],
        )
    );
  }
}
